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
package com.infomaniak.multiplatform_core.contacts.utils

import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** OAuth tokens by account, kept in memory only. */
internal class TokenStore {

    private val mutex = Mutex()
    private val tokens = mutableMapOf<AccountId, AccessToken>()

    suspend fun get(accountId: AccountId): AccessToken? = mutex.withLock { tokens[accountId] }

    suspend fun accountIds(): Set<AccountId> = mutex.withLock { tokens.keys.toSet() }

    suspend fun put(accountId: AccountId, accessToken: AccessToken) {
        mutex.withLock { tokens[accountId] = accessToken }
    }

    suspend fun remove(accountId: AccountId) {
        mutex.withLock { tokens.remove(accountId) }
    }
}
