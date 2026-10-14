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

import com.infomaniak.multiplatform_calendar.meeting_rooms.utils.TokenStore
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlin.coroutines.cancellation.CancellationException

/** Entry point of the meeting rooms module: the Infomaniak meeting rooms an account can book. */
public class MeetingRoomsManager {

    private val tokenStore = TokenStore()

    /** Registers [accountId] with its [accessToken], kept in memory only. Call it at startup and on login. */
    @Throws(CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, accessToken: AccessToken): Unit =
        tokenStore.put(accountId, accessToken)

    /** Forgets the token of [accountId]. */
    @Throws(CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = tokenStore.remove(accountId)
}
