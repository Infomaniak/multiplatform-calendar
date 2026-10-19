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

import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.MeetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.MeetingRoomsRemoteDataSource
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.createMeetingRoomsHttpClient
import com.infomaniak.multiplatform_calendar.bookable_resources.data.repository.MeetingRoomsRepository
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.MeetingRoom
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.MeetingRoomUuid
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.MeetingRoomsException
import com.infomaniak.multiplatform_calendar.bookable_resources.utils.meetingRoomsCall
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.account.utils.AccountTokenStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

public class MeetingRoomsManager internal constructor(
    internal val database: MeetingRoomsDatabase,
    httpClient: HttpClient,
) {

    private val repository = MeetingRoomsRepository(
        database = database,
        remoteDataSource = MeetingRoomsRemoteDataSource(httpClient),
        tokenStore = AccountTokenStore(),
    )

    /** @param database Host Room database declaring the meeting rooms tables. */
    public constructor(database: MeetingRoomsDatabase) : this(
        database = database,
        httpClient = createMeetingRoomsHttpClient(),
    )

    /** The token is kept in memory only: call it at startup and on login. Does not sync. */
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, accessToken: AccessToken): Unit =
        meetingRoomsCall { repository.initAccount(accountId, accessToken) }

    /** Also deletes the local meeting rooms of [accountId]. */
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = meetingRoomsCall { repository.removeAccount(accountId) }

    /**
     * When some accounts fail, the others still complete, then the first failure is thrown.
     * @param accountIds Every initialized account when empty.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun sync(accountIds: Set<AccountId> = emptySet()): Unit = meetingRoomsCall { repository.sync(accountIds) }

    /** Sorted by name. */
    public fun observeRooms(accountId: AccountId): Flow<List<MeetingRoom>> =
        repository.observeRooms(accountId).flowOn(Dispatchers.Default)

    /** A room missing locally is fetched; null when that fails or the account cannot book it. */
    public fun observeRoom(accountId: AccountId, uuid: MeetingRoomUuid): Flow<MeetingRoom?> =
        repository.observeRoom(accountId, uuid).flowOn(Dispatchers.Default)

    /**
     * Always hits the network.
     * @param rrule RFC 5545 rule, checked over 3 months from [start] in [timeZone].
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun availability(
        accountId: AccountId,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
        rrule: String? = null,
    ): Map<MeetingRoomUuid, Boolean> = meetingRoomsCall {
        repository.availability(accountId, uuid = null, start, end, timeZone, rrule)
    }

    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun isAvailable(
        accountId: AccountId,
        uuid: MeetingRoomUuid,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
        rrule: String? = null,
    ): Boolean = meetingRoomsCall {
        repository.availability(accountId, uuid, start, end, timeZone, rrule)[uuid] == true
    }

    /** Rooms the account cannot book are left out. Hits the network unless [uuids] is empty. */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun busyPeriods(
        accountId: AccountId,
        uuids: Set<MeetingRoomUuid>,
        from: Instant,
        to: Instant,
    ): Map<MeetingRoomUuid, List<BusyPeriod>> = meetingRoomsCall { repository.busyPeriods(accountId, uuids, from, to) }
}
