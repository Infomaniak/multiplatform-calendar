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

import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.MeetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.EquipmentEntity
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.MeetingRoomEntity
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.MeetingRoomsRemoteDataSource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBookableResource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.parseHexColor
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.Equipment
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoom
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomUuid
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
import com.infomaniak.multiplatform_calendar.meeting_rooms.utils.TokenStore
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

internal class MeetingRoomsRepository(
    database: MeetingRoomsDatabase,
    private val remoteDataSource: MeetingRoomsRemoteDataSource,
    private val tokenStore: TokenStore,
) {

    private val dao = database.meetingRoomDao()
    private val syncMutexes = mutableMapOf<AccountId, Mutex>()
    private val syncMutexesLock = Mutex()

    suspend fun initAccount(accountId: AccountId, accessToken: AccessToken) = tokenStore.put(accountId, accessToken)

    // Under the sync mutex, so that a running sync cannot write the account back.
    suspend fun removeAccount(accountId: AccountId) {
        syncMutex(accountId).withLock {
            tokenStore.remove(accountId)
            dao.deleteRoomsForAccount(accountId)
        }
    }

    suspend fun sync(accountIds: Set<AccountId>) {
        val failures = coroutineScope {
            accountIds.ifEmpty { tokenStore.getAccountIds() }
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
            val rooms = remoteDataSource.getBookableResources(getToken(accountId))
                .filter(ApiBookableResource::isMeetingRoom)
                .map { it.toEntity(accountId) }
            dao.replaceRooms(accountId, rooms)
        }
    }

    private suspend fun getToken(accountId: AccountId): AccessToken =
        tokenStore.getToken(accountId) ?: throw MeetingRoomsException(MeetingRoomsErrorCause.AccountNotInitialized(accountId))

    private suspend fun syncMutex(accountId: AccountId): Mutex = syncMutexesLock.withLock {
        syncMutexes.getOrPut(accountId) { Mutex() }
    }
}

private fun ApiBookableResource.toEntity(accountId: AccountId): MeetingRoomEntity = MeetingRoomEntity(
    accountId = accountId,
    uuid = MeetingRoomUuid(uuid),
    name = name,
    color = parseHexColor(color),
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
