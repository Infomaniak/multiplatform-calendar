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
package com.infomaniak.multiplatform_calendar.core.managers

import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemote
import com.infomaniak.multiplatform_calendar.core.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.account.DavCredentials
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
public class AccountManager internal constructor(
    private val accountRepository: AccountRepository,
) {

    public suspend fun initAccount(accountId: AccountId, credentials: DavCredentials): Unit = withContext(Dispatchers.Default) {
        accountRepository.storeCredentials(accountId, credentials.toRemote())
    }

    public suspend fun removeAccount(accountId: AccountId): Unit = withContext(Dispatchers.Default) {
        accountRepository.removeCredentials(accountId)
    }

    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun retrieveCaldavPassword(authToken: String): String {
        return accountRepository.retrieveCaldavPassword(authToken)
    }
}

