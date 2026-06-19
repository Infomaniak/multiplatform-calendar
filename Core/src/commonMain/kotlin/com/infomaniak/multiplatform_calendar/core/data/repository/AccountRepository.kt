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
package com.infomaniak.multiplatform_calendar.core.data.repository

import com.infomaniak.multiplatform_calendar.core.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.remote.AuthDataSource
import com.infomaniak.multiplatform_calendar.core.di.SdkScope
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.account.DavCredentials
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.coroutines.cancellation.CancellationException

@SingleIn(SdkScope::class)
@Inject
internal class AccountRepository(
    private val accountDao: AccountDao,
    private val authDataSource: AuthDataSource,
) {
    private val _currentAccountId = MutableSharedFlow<AccountId?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val currentAccountIdFlow = _currentAccountId.asSharedFlow()

    private val userCredentials: HashMap<AccountId, DavAccount> = HashMap()

    fun getCredentials(accountId: AccountId): DavAccount? {
        return userCredentials[accountId]
    }

    suspend fun storeCredentials(accountId: AccountId, credentials: DavAccount) {
        userCredentials[accountId] = credentials
        accountDao.insert(AccountEntity(id = accountId))
        _currentAccountId.emit(accountId)
    }

    suspend fun removeCredentials(accountId: AccountId) {
        userCredentials.remove(accountId)
        accountDao.delete(accountId)
        if (_currentAccountId.replayCache.lastOrNull() == accountId) {
            _currentAccountId.emit(null)
        }
    }

    @Throws(CalendarSdkException::class, CancellationException::class)
    suspend fun retrieveDavCredential(authToken: String, login: String? = null): DavCredentials {
        return runCatching {
            val login = login ?: authDataSource.retrieveUserProfile(authToken).login
            val password = authDataSource.exchangeTokenToPassword(authToken)
            DavCredentials(
                username = login,
                password = password.password,
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            else throw CalendarSdkException(it.message, it)
        }
    }
}
