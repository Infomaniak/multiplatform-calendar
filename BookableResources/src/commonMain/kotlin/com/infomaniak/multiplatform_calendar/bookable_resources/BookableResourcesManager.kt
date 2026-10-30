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
package com.infomaniak.multiplatform_calendar.bookable_resources

import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.BookableResourcesDatabase
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.BookableResourcesRemoteDataSource
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.createBookableResourcesHttpClient
import com.infomaniak.multiplatform_calendar.bookable_resources.data.repository.BookableResourcesRepository
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResource
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesException
import com.infomaniak.multiplatform_calendar.bookable_resources.utils.bookableResourcesCall
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.account.utils.InMemoryAccountTokens
import com.infomaniak.multiplatform_core.network.ApiEnvironment
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

public class BookableResourcesManager internal constructor(
    internal val database: BookableResourcesDatabase,
    httpClient: HttpClient,
) {

    private val repository = BookableResourcesRepository(
        database = database,
        remoteDataSource = BookableResourcesRemoteDataSource(httpClient),
        tokenByAccount = InMemoryAccountTokens(),
    )

    /** @param database Host Room database declaring the bookable resources tables. */
    public constructor(database: BookableResourcesDatabase, apiEnvironment: ApiEnvironment) : this(
        database = database,
        httpClient = createBookableResourcesHttpClient(apiEnvironment),
    )

    /** The token is kept in memory only: call it at startup and on login. Does not sync. */
    @Throws(BookableResourcesException::class, CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, accessToken: AccessToken): Unit =
        bookableResourcesCall { repository.initAccount(accountId, accessToken) }

    /** Also deletes the local bookable resources of [accountId]. */
    @Throws(BookableResourcesException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = bookableResourcesCall { repository.removeAccount(accountId) }

    /**
     * When some accounts fail, the others still complete, then the first failure is thrown.
     * @param accountIds Every initialized account when empty.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(BookableResourcesException::class, CancellationException::class)
    public suspend fun sync(accountIds: Set<AccountId> = emptySet()): Unit = bookableResourcesCall { repository.sync(accountIds) }

    /** Sorted by name. */
    public fun observeResources(accountId: AccountId): Flow<List<BookableResource>> =
        repository.observeResources(accountId).flowOn(Dispatchers.Default)

    /** A resource missing locally is fetched; null when that fails or the account cannot book it. */
    public fun observeResource(accountId: AccountId, uuid: BookableResourceUuid): Flow<BookableResource?> =
        repository.observeResource(accountId, uuid).flowOn(Dispatchers.Default)

    /**
     * Always hits the network.
     * @param rrule RFC 5545 rule, checked over 3 months from [start] in [timeZone].
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(BookableResourcesException::class, CancellationException::class)
    public suspend fun availability(
        accountId: AccountId,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
        rrule: String? = null,
    ): Map<BookableResourceUuid, Boolean> = bookableResourcesCall {
        repository.availability(accountId, uuid = null, start, end, timeZone, rrule)
    }

    @Throws(BookableResourcesException::class, CancellationException::class)
    public suspend fun isAvailable(
        accountId: AccountId,
        uuid: BookableResourceUuid,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
        rrule: String? = null,
    ): Boolean = bookableResourcesCall {
        repository.availability(accountId, uuid, start, end, timeZone, rrule)[uuid] == true
    }

    /** Resources the account cannot book are left out. Hits the network unless [uuids] is empty. */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(BookableResourcesException::class, CancellationException::class)
    public suspend fun busyPeriods(
        accountId: AccountId,
        uuids: Set<BookableResourceUuid>,
        from: Instant,
        to: Instant,
    ): Map<BookableResourceUuid, List<BusyPeriod>> = bookableResourcesCall { repository.busyPeriods(accountId, uuids, from, to) }
}
