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
package com.infomaniak.multiplatform_calendar.core.extensions

import com.infomaniak.multiplatform_calendar.core.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount

/**
 * Synchronizes all accounts with the provided [syncAction] function.
 * If the list of accounts changes during the synchronization, the process will restart with the new list of accounts.
 *
 * @param syncAction The action to perform for each account. It takes an [AccountId] and a [DavAccount] as parameters.
 */
internal suspend fun AccountRepository.syncAccountsWithRestartingCollection(
    syncAction: suspend (AccountId, DavAccount) -> Unit,
) {
    val initialAccountIds = currentAccountIdsSnapshot()
    if (initialAccountIds.isEmpty()) return

    var currentAccountId: AccountId? = null
    currentAccountIdsFlow.collectRestartingUntilComplete(
        initialValue = initialAccountIds,
        shouldRestart = { newAccountIds ->
            shouldRestartCurrentSync(currentAccountId, newAccountIds)
        },
        action = { accountIds ->
            try {
                accountIds.forEach { accountId ->
                    currentAccountId = accountId
                    syncAccountIfConnected(accountId, syncAction)
                }
            } finally {
                currentAccountId = null
            }
        },
    )
}

private fun AccountRepository.currentAccountIdsSnapshot(): Set<AccountId> {
    return currentAccountIdsFlow.replayCache.lastOrNull().orEmpty()
}

private fun shouldRestartCurrentSync(currentAccountId: AccountId?, newAccountIds: Set<AccountId>): Boolean {
    return newAccountIds.isEmpty() || (currentAccountId != null && currentAccountId !in newAccountIds)
}

private suspend fun AccountRepository.syncAccountIfConnected(
    accountId: AccountId,
    syncAction: suspend (AccountId, DavAccount) -> Unit,
) {
    if (accountId !in currentAccountIdsSnapshot()) return
    val credentials = getCredentialsOrNull(accountId) ?: return
    syncAction(accountId, credentials)
}

private suspend fun AccountRepository.getCredentialsOrNull(accountId: AccountId): DavAccount? {
    return try {
        getCredentials(accountId)
    } catch (_: IllegalStateException) {
        null
    }
}

