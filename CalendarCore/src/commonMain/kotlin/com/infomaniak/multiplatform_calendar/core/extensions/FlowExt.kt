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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Runs [action] for [initialValue] and conditionally restarts it when this flow emits a new value.
 *
 * When [shouldRestart] returns `true`, the currently running action is cancelled and restarted
 * with the new emitted value. If [action] completes without restart, collection stops and this
 * function returns.
 */
internal suspend fun <T> Flow<T>.collectRestartingUntilComplete(
    initialValue: T,
    shouldRestart: (newValue: T) -> Boolean,
    action: suspend (T) -> Unit,
) = coroutineScope {
    val updates = Channel<T>(Channel.CONFLATED)
    val collectorJob = launchCollectingIntoChannel(this@coroutineScope, updates)

    var runningActionJob = launch { action(initialValue) }

    try {
        while (runningActionJob.isActive) {
            // Wait for either the action to complete or a new value to be emitted
            select {
                // Wait for the action to complete
                runningActionJob.onJoin {
                    // The action completed normally: stop listening for updates.
                }

                // Wait for a new value to be emitted
                updates.onReceiveCatching { result ->
                    val newValue = result.getOrNull() ?: return@onReceiveCatching
                    if (!shouldRestart(newValue)) return@onReceiveCatching
                    // Cancel the current action and start a new one with the new value
                    runningActionJob.cancelAndJoin()
                    runningActionJob = launch { action(newValue) }
                }
            }
        }
    } finally {
        // Cancel the collector and action jobs when the flow collection is complete or cancelled
        collectorJob.cancelAndJoin()
        updates.close()
        runningActionJob.cancelAndJoin()
    }
}

private fun <T> Flow<T>.launchCollectingIntoChannel(
    scope: CoroutineScope,
    updates: SendChannel<T>,
): Job = scope.launch {
    collect { value ->
        updates.trySend(value)
    }
}
