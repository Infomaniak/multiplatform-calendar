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
package com.infomaniak.multiplatform_calendar.meeting_rooms

import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.MeetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.MeetingRoomsDatabaseSource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.meetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.MeetingRoomsRemoteDataSource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.createMeetingRoomsHttpClient
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.repository.MeetingRoomsRepository
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoom
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
import com.infomaniak.multiplatform_calendar.meeting_rooms.utils.TokenStore
import com.infomaniak.multiplatform_calendar.meeting_rooms.utils.meetingRoomsCall
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Entry point of the meeting rooms module: the Infomaniak meeting rooms an account can book. */
public class MeetingRoomsManager internal constructor(
    internal val database: MeetingRoomsDatabase,
    httpClient: HttpClient,
) {

    private val repository = MeetingRoomsRepository(
        database = database,
        remoteDataSource = MeetingRoomsRemoteDataSource(httpClient),
        tokenStore = TokenStore(),
    )

    /** @param databasePath Absolute path of the meeting rooms database file. */
    public constructor(databasePath: String) : this(
        database = meetingRoomsDatabase(MeetingRoomsDatabaseSource.File(databasePath)),
        httpClient = createMeetingRoomsHttpClient(),
    )

    /**
     * Registers [accountId] with its [accessToken], kept in memory only. Call it at startup and on login; it does not
     * sync, call [sync] for that.
     */
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, accessToken: AccessToken): Unit =
        meetingRoomsCall { repository.initAccount(accountId, accessToken) }

    /** Forgets the token of [accountId] and deletes its local meeting rooms. */
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = meetingRoomsCall { repository.removeAccount(accountId) }

    /**
     * Replaces the local meeting rooms with the ones the server lets each account book, in parallel. When some accounts
     * fail, the others still complete, then the first failure is thrown.
     *
     * @param accountIds Accounts to sync, or every initialized account when empty.
     * @throws MeetingRoomsException [MeetingRoomsErrorCause.AccountNotInitialized] for a requested account without a token.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(MeetingRoomsException::class, CancellationException::class)
    public suspend fun sync(accountIds: Set<AccountId> = emptySet()): Unit = meetingRoomsCall { repository.sync(accountIds) }

    /** The local meeting rooms of [accountId], sorted by name. */
    public fun observeRooms(accountId: AccountId): Flow<List<MeetingRoom>> =
        repository.observeRooms(accountId).flowOn(Dispatchers.Default)

    /**
     * The local meeting room [uuid] of [accountId], typically the `X-INFOMANIAK-BOOKABLE` value of an event. A room
     * missing locally is fetched from the server; it stays null when that fails or the account cannot book it.
     */
    public fun observeRoom(accountId: AccountId, uuid: String): Flow<MeetingRoom?> =
        repository.observeRoom(accountId, uuid).flowOn(Dispatchers.Default)
}
