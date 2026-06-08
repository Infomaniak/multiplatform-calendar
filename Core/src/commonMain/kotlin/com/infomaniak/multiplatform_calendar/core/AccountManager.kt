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
package com.infomaniak.multiplatform_calendar.core

import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemote
import com.infomaniak.multiplatform_calendar.core.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.core.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.account.DavCredentials
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.invoke

@SingleIn(AppScope::class)
@Inject
class AccountManager private constructor(
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
) {

    suspend fun initAccount(accountId: AccountId, credentials: DavCredentials) = Dispatchers.IO {
        accountRepository.storeCredentials(accountId, credentials.toRemote())
    }

    suspend fun syncCalendars(accountId: AccountId) = Dispatchers.IO {
        accountRepository.getCredentials(accountId)?.let { credentials ->
            calendarRepository.syncCalendars(accountId = accountId, credentials = credentials)
        }
    }

    suspend fun removeAccount(accountId: AccountId) = Dispatchers.IO {
        accountRepository.removeCredentials(accountId)
    }
}

