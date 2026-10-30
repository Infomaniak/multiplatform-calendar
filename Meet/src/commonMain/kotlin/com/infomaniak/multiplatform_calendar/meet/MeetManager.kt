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
package com.infomaniak.multiplatform_calendar.meet

import com.infomaniak.multiplatform_calendar.meet.data.remote.MeetRemoteDataSource
import com.infomaniak.multiplatform_calendar.meet.data.remote.createMeetHttpClient
import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetErrorCause
import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetException
import com.infomaniak.multiplatform_calendar.meet.utils.MeetCaller
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.account.utils.InMemoryAccountTokens
import com.infomaniak.multiplatform_core.crashreport.CrashReport
import io.ktor.client.HttpClient
import kotlin.coroutines.cancellation.CancellationException

public class MeetManager internal constructor(httpClient: HttpClient, crashReport: CrashReport) {

    private val meetCaller = MeetCaller(crashReport)
    private val remoteDataSource = MeetRemoteDataSource(httpClient)
    private val tokenByAccount = InMemoryAccountTokens()

    public constructor(crashReport: CrashReport) : this(httpClient = createMeetHttpClient(), crashReport = crashReport)

    @Throws(MeetException::class, CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, accessToken: AccessToken): Unit =
        meetCaller { tokenByAccount.put(accountId, accessToken) }

    @Throws(MeetException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = meetCaller { tokenByAccount.remove(accountId) }

    /** Each call creates a new kMeet room. */
    @Throws(MeetException::class, CancellationException::class)
    public suspend fun generateRoomUrl(accountId: AccountId): String = meetCaller {
        val token = tokenByAccount.getToken(accountId) ?: throw MeetException(MeetErrorCause.AccountNotInitialized(accountId))
        remoteDataSource.getMeetRoomUrl(token)
    }
}
