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
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@SingleIn(AppScope::class)
@Inject
public class AccountManager internal constructor(
    private val accountRepository: AccountRepository,
    private val sdkCaller: SdkCaller,
) {

    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, credentials: DavCredentials): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "initAccount $accountId") {
            accountRepository.storeCredentials(accountId, credentials.toRemote())
        }
    }

    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "removeAccount $accountId") {
            accountRepository.removeCredentials(accountId)
        }
    }

    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun retrieveDavCredential(authToken: String, login: String? = null): DavCredentials {
        return sdkCaller.run(operation = "retrieveDavCredential") {
            accountRepository.retrieveDavCredential(authToken, login)
        }
    }
}

