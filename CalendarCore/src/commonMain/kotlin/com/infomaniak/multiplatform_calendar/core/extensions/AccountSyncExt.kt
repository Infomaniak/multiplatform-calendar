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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal enum class SyncResult { Completed, NeedsResync, }

/**
 * Synchronizes accounts from a snapshot without restarting when the account list changes.
 *
 * Detects newly added accounts to request a follow-up pass, while tracking removed accounts
 * to skip pending synchronizations or cancel the currently running one.
 *
 * @param syncAction The synchronization operation to execute for each account.
 * @return [SyncResult.NeedsResync] if new accounts were detected during the pass,
 * or [SyncResult.Completed] otherwise.
 */
@OptIn(ExperimentalAtomicApi::class)
internal suspend fun AccountRepository.syncCurrentAccounts(
    syncAction: suspend (AccountId, DavAccount) -> Unit,
): SyncResult = coroutineScope {
    val initialAccountIds = currentAccountIdsSnapshot()
    if (initialAccountIds.isEmpty()) return@coroutineScope SyncResult.Completed

    val needsResync = AtomicBoolean(false)
    val removedAccounts = mutableSetOf<AccountId>()
    var currentAccountId: AccountId? = null
    var currentAccountJob: Job? = null

    val observer = launchAccountIdsObserver(
        scope = this,
        initialAccountIds = initialAccountIds,
        removedAccounts = removedAccounts,
        needsResync = needsResync,
        currentAccountIdProvider = { currentAccountId },
        cancelCurrentAccountSync = { currentAccountJob?.cancel() },
    )

    try {
        initialAccountIds.forEach { accountId ->
            if (accountId in removedAccounts) return@forEach

            currentAccountId = accountId
            currentAccountJob = launch(start = CoroutineStart.UNDISPATCHED) {
                syncAccountIfConnected(accountId, syncAction)
            }

            try {
                currentAccountJob.join()
            } finally {
                currentAccountJob = null
                currentAccountId = null
            }
        }
    } finally {
        observer.cancelAndJoin()
    }

    if (needsResync.load()) SyncResult.NeedsResync else SyncResult.Completed
}


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
    var plannedAccountIds: Set<AccountId> = initialAccountIds
    currentAccountIdsFlow.collectRestartingUntilComplete(
        initialValue = initialAccountIds,
        shouldRestart = { newAccountIds ->
            shouldRestartCurrentSync(currentAccountId, plannedAccountIds, newAccountIds).also { restart ->
                if (restart) plannedAccountIds = newAccountIds
            }
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

private fun shouldRestartCurrentSync(
    currentAccountId: AccountId?,
    currentRunAccountIds: Set<AccountId>,
    newAccountIds: Set<AccountId>,
): Boolean {
    if (newAccountIds.isEmpty()) return true
    if (currentAccountId != null && currentAccountId !in newAccountIds) return true
    return newAccountIds.any { it !in currentRunAccountIds }
}

@OptIn(ExperimentalAtomicApi::class)
private fun AccountRepository.launchAccountIdsObserver(
    scope: CoroutineScope,
    initialAccountIds: Set<AccountId>,
    removedAccounts: MutableSet<AccountId>,
    needsResync: AtomicBoolean,
    currentAccountIdProvider: () -> AccountId?,
    cancelCurrentAccountSync: () -> Unit,
): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
    var previousAccountIds = initialAccountIds

    currentAccountIdsFlow.collect { newAccountIds ->
        // Detect newly added accounts.
        if (!needsResync.load() && newAccountIds.any { it !in previousAccountIds }) {
            needsResync.store(true)
        }
        // Track removed accounts during this pass.
        removedAccounts += initialAccountIds - newAccountIds
        // Cancel only the currently syncing account if removed.
        if (currentAccountIdProvider() in removedAccounts) cancelCurrentAccountSync()
        previousAccountIds = newAccountIds
    }
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
