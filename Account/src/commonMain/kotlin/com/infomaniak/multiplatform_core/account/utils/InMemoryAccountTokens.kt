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
package com.infomaniak.multiplatform_core.account.utils

import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public class InMemoryAccountTokens {

    private val mutex = Mutex()
    private val tokens = mutableMapOf<AccountId, AccessToken>()

    public suspend fun getToken(accountId: AccountId): AccessToken? = mutex.withLock { tokens[accountId] }

    public suspend fun getAccountIds(): Set<AccountId> = mutex.withLock { tokens.keys.toSet() }

    public suspend fun put(accountId: AccountId, accessToken: AccessToken) {
        mutex.withLock { tokens[accountId] = accessToken }
    }

    public suspend fun remove(accountId: AccountId) {
        mutex.withLock { tokens.remove(accountId) }
    }
}
