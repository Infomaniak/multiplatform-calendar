/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.multiplatform_calendar.meeting_rooms.data.repository

import androidx.room3.withWriteTransaction
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.MeetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.EquipmentEntity
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.MeetingRoomEntity
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.MeetingRoomsRemoteDataSource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiAvailabilityRequest
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBookableResource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBusyPeriod
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBusyPeriodsRequest
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.Equipment
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoom
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
import com.infomaniak.multiplatform_calendar.meeting_rooms.utils.TokenStore
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

internal class MeetingRoomsRepository(
    private val database: MeetingRoomsDatabase,
    private val remoteDataSource: MeetingRoomsRemoteDataSource,
    private val tokenStore: TokenStore,
) {

    private val dao = database.meetingRoomDao()
    private val syncMutexes = mutableMapOf<AccountId, Mutex>()
    private val syncMutexesLock = Mutex()

    suspend fun initAccount(accountId: AccountId, accessToken: AccessToken) = tokenStore.put(accountId, accessToken)

    /** Takes the sync mutex of [accountId] so that a running sync cannot write the account back after its removal. */
    suspend fun removeAccount(accountId: AccountId) {
        syncMutex(accountId).withLock {
            tokenStore.remove(accountId)
            dao.deleteRoomsForAccount(accountId)
        }
    }

    /**
     * Syncs [accountIds], or every initialized account when empty, in parallel. When some accounts fail, the others
     * still complete, then the first failure is thrown with the next ones suppressed.
     */
    suspend fun sync(accountIds: Set<AccountId>) {
        val failures = coroutineScope {
            accountIds.ifEmpty { tokenStore.accountIds() }
                .map { accountId -> async { syncFailure(accountId) } }
                .awaitAll()
                .filterNotNull()
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    fun observeRooms(accountId: AccountId): Flow<List<MeetingRoom>> =
        dao.observeRooms(accountId).map { rooms -> rooms.map(MeetingRoomEntity::toMeetingRoom) }

    /** Emits the local room right away, while a room missing locally is fetched in the background. */
    fun observeRoom(accountId: AccountId, uuid: String): Flow<MeetingRoom?> = channelFlow {
        launch { fetchMissingRoom(accountId, uuid) }
        dao.observeRoom(accountId, uuid).collect { send(it?.toMeetingRoom()) }
    }

    suspend fun availability(
        accountId: AccountId,
        uuid: String?,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
        rrule: String?,
    ): Map<String, Boolean> {
        val request = ApiAvailabilityRequest(
            start = start.toApiDateTime(timeZone),
            end = end.toApiDateTime(timeZone),
            timezoneStart = timeZone.id,
            timezoneEnd = timeZone.id,
            rrule = rrule,
        )
        return remoteDataSource.getAvailability(token(accountId), request, uuid)
    }

    suspend fun busyPeriods(
        accountId: AccountId,
        uuids: Set<String>,
        from: Instant,
        to: Instant,
    ): Map<String, List<BusyPeriod>> {
        if (uuids.isEmpty()) return emptyMap()

        val request = ApiBusyPeriodsRequest(
            bookableResourceUuids = uuids.toList(),
            from = from.toApiDateTime(TimeZone.UTC),
            to = to.toApiDateTime(TimeZone.UTC),
        )
        return remoteDataSource.getBusyPeriods(token(accountId), request)
            .mapValues { (_, periods) -> periods.map(ApiBusyPeriod::toBusyPeriod) }
    }

    private suspend fun syncFailure(accountId: AccountId): Exception? = try {
        syncAccount(accountId)
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        exception
    }

    private suspend fun syncAccount(accountId: AccountId) {
        syncMutex(accountId).withLock {
            val rooms = remoteDataSource.getBookableResources(token(accountId))
                .filter(ApiBookableResource::isMeetingRoom)
                .map { it.toEntity(accountId) }
            database.withWriteTransaction {
                dao.deleteRoomsForAccount(accountId)
                dao.upsertRooms(rooms)
            }
        }
    }

    /** Failures are ignored: the room then stays unknown. */
    private suspend fun fetchMissingRoom(accountId: AccountId, uuid: String) {
        try {
            if (dao.hasRoom(accountId, uuid)) return
            val token = tokenStore.get(accountId) ?: return
            val room = remoteDataSource.getBookableResource(token, uuid)?.takeIf(ApiBookableResource::isMeetingRoom) ?: return
            syncMutex(accountId).withLock {
                if (tokenStore.get(accountId) != null) dao.upsertRooms(listOf(room.toEntity(accountId)))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
        }
    }

    private suspend fun token(accountId: AccountId): AccessToken =
        tokenStore.get(accountId) ?: throw MeetingRoomsException(MeetingRoomsErrorCause.AccountNotInitialized(accountId))

    private suspend fun syncMutex(accountId: AccountId): Mutex = syncMutexesLock.withLock {
        syncMutexes.getOrPut(accountId) { Mutex() }
    }
}

/** `Y-m-d H:i:s`, the date format of the API. */
private val apiDateTimeFormat = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
}

private fun Instant.toApiDateTime(timeZone: TimeZone): String = toLocalDateTime(timeZone).format(apiDateTimeFormat)

private fun ApiBookableResource.toEntity(accountId: AccountId): MeetingRoomEntity = MeetingRoomEntity(
    accountId = accountId,
    uuid = uuid,
    name = name,
    color = color,
    description = description,
    location = location,
    floor = floor,
    capacity = capacity,
    equipments = equipments.map { EquipmentEntity(name = it.name, category = it.category) },
)

private fun MeetingRoomEntity.toMeetingRoom(): MeetingRoom = MeetingRoom(
    uuid = uuid,
    name = name,
    color = color,
    description = description,
    location = location,
    floor = floor,
    capacity = capacity,
    equipments = equipments.map { Equipment(name = it.name, category = it.category) },
)

private fun ApiBusyPeriod.toBusyPeriod(): BusyPeriod = BusyPeriod(
    start = Instant.parse(start),
    end = Instant.parse(end),
    isFullDay = fullday,
    title = title,
    organizer = organizer,
    isOwner = isOwner,
)
