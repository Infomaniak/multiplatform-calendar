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
package com.infomaniak.multiplatform_calendar.bookable_resources.data.repository

import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.BookableResourcesDatabase
import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.BookableResourceEntity
import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.EquipmentEntity
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.BookableResourcesRemoteDataSource
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiAvailabilityRequest
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiBookableResource
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiBusyPeriod
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiBusyPeriodsRequest
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.parseHexColor
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResource
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.Equipment
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesErrorCause
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.account.utils.InMemoryAccountTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

internal class BookableResourcesRepository(
    database: BookableResourcesDatabase,
    private val remoteDataSource: BookableResourcesRemoteDataSource,
    private val tokenStore: InMemoryAccountTokens,
) {

    private val dao = database.bookableResourceDao()
    private val syncMutexes = mutableMapOf<AccountId, Mutex>()
    private val syncMutexesLock = Mutex()
    private val resourceFetchMutexes = mutableMapOf<Pair<AccountId, BookableResourceUuid>, Mutex>()
    private val resourceFetchMutexesLock = Mutex()

    suspend fun initAccount(accountId: AccountId, accessToken: AccessToken) = tokenStore.put(accountId, accessToken)

    // Under the sync mutex, so that a running sync cannot write the account back.
    suspend fun removeAccount(accountId: AccountId) {
        syncMutex(accountId).withLock {
            tokenStore.remove(accountId)
            dao.deleteResourcesForAccount(accountId)
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

    fun observeResources(accountId: AccountId): Flow<List<BookableResource>> =
        dao.observeResources(accountId).map { resources -> resources.map(BookableResourceEntity::toBookableResource) }

    fun observeResource(accountId: AccountId, uuid: BookableResourceUuid): Flow<BookableResource?> = channelFlow {
        launch { fetchMissingResource(accountId, uuid) }
        dao.observeResource(accountId, uuid).collect { send(it?.toBookableResource()) }
    }

    suspend fun availability(
        accountId: AccountId,
        uuid: BookableResourceUuid?,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
        rrule: String?,
    ): Map<BookableResourceUuid, Boolean> {
        val request = ApiAvailabilityRequest(
            start = start.toLocalDateTime(timeZone),
            end = end.toLocalDateTime(timeZone),
            timezoneStart = timeZone.id,
            timezoneEnd = timeZone.id,
            rrule = rrule,
        )
        return remoteDataSource.getAvailability(getToken(accountId), request, uuid)
            .mapKeys { (key, _) -> BookableResourceUuid(key) }
    }

    suspend fun busyPeriods(
        accountId: AccountId,
        uuids: Set<BookableResourceUuid>,
        from: Instant,
        to: Instant,
    ): Map<BookableResourceUuid, List<BusyPeriod>> {
        if (uuids.isEmpty()) return emptyMap()

        val request = ApiBusyPeriodsRequest(
            bookableResourceUuids = uuids.map(BookableResourceUuid::value),
            from = from.toLocalDateTime(TimeZone.UTC),
            to = to.toLocalDateTime(TimeZone.UTC),
        )
        return remoteDataSource.getBusyPeriods(getToken(accountId), request)
            .entries
            .associate { (key, periods) -> BookableResourceUuid(key) to periods.map(ApiBusyPeriod::toBusyPeriod) }
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
            val resources = remoteDataSource.getBookableResources(getToken(accountId))
                .filter(ApiBookableResource::isMeetingRoom)
                .map { it.toEntity(accountId) }
            dao.replaceResources(accountId, resources)
        }
    }

    // Failures are ignored. Concurrent collectors wait for the fetch in progress, then fetch only if the resource is still missing.
    private suspend fun fetchMissingResource(accountId: AccountId, uuid: BookableResourceUuid) {
        resourceFetchMutex(accountId to uuid).withLock {
            try {
                if (dao.hasResource(accountId, uuid)) return
                val token = tokenStore.getToken(accountId) ?: return
                val resource = remoteDataSource.getBookableResource(token, uuid)?.takeIf(ApiBookableResource::isMeetingRoom) ?: return
                syncMutex(accountId).withLock {
                    if (tokenStore.getToken(accountId) != null) dao.upsertResources(listOf(resource.toEntity(accountId)))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun getToken(accountId: AccountId): AccessToken =
        tokenStore.getToken(accountId) ?: throw BookableResourcesException(BookableResourcesErrorCause.AccountNotInitialized(accountId))

    private suspend fun syncMutex(accountId: AccountId): Mutex = syncMutexesLock.withLock {
        syncMutexes.getOrPut(accountId) { Mutex() }
    }

    private suspend fun resourceFetchMutex(key: Pair<AccountId, BookableResourceUuid>): Mutex = resourceFetchMutexesLock.withLock {
        resourceFetchMutexes.getOrPut(key) { Mutex() }
    }
}

private fun ApiBookableResource.toEntity(accountId: AccountId): BookableResourceEntity = BookableResourceEntity(
    accountId = accountId,
    uuid = BookableResourceUuid(uuid),
    name = name,
    color = parseHexColor(color),
    description = description,
    location = location,
    floor = floor,
    capacity = capacity,
    equipments = equipments.map { EquipmentEntity(name = it.name, category = it.category) },
)

private fun BookableResourceEntity.toBookableResource(): BookableResource = BookableResource(
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
    start = start,
    end = end,
    isFullDay = fullday,
    title = title,
    organizer = organizer,
    isOwner = isOwner,
)
