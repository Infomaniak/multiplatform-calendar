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
import com.infomaniak.multiplatform_calendar.core.domain.model.account.DavCredentials
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.ContactsManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@SingleIn(AppScope::class)
@Inject
public class AccountManager internal constructor(
    private val accountRepository: AccountRepository,
    private val contactsManager: ContactsManager,
    private val sdkCaller: SdkCaller,
) {

    /** Makes each [initAccount] / [removeAccount] update the calendar and the contacts as a single step. */
    private val accountLifecycleMutex = Mutex()

    /** Registers [accountId] for the calendar and the contacts. Call it at startup and on login. */
    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun initAccount(
        accountId: AccountId,
        credentials: DavCredentials,
        accessToken: AccessToken,
    ): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "initAccount $accountId") {
            accountLifecycleMutex.withLock {
                accountRepository.storeCredentials(accountId, credentials.toRemote())
                contactsManager.initAccount(accountId, accessToken)
            }
        }
    }

    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "removeAccount $accountId") {
            accountLifecycleMutex.withLock {
                accountRepository.removeCredentials(accountId)
                contactsManager.removeAccount(accountId)
            }
        }
    }

    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun retrieveDavCredential(authToken: AccessToken, login: String? = null): DavCredentials {
        return sdkCaller.run(operation = "retrieveDavCredential") {
            accountRepository.retrieveDavCredential(authToken, login)
        }
    }
}

