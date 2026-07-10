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

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
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
    shouldRestart: (currentValue: T, newValue: T) -> Boolean,
    action: suspend (T) -> Unit,
) = coroutineScope {
    var runningValue = initialValue
    val updates = Channel<T>(Channel.CONFLATED)
    val collectorJob = launch {
        collect { value ->
            updates.trySend(value)
        }
    }
    var actionJob = launch { action(runningValue) }

    try {
        var isCompleted = false
        while (!isCompleted) {
            select {
                actionJob.onJoin {
                    isCompleted = true
                }

                updates.onReceiveCatching { result ->
                    val newValue = result.getOrNull() ?: return@onReceiveCatching
                    if (!shouldRestart(runningValue, newValue)) return@onReceiveCatching

                    runningValue = newValue
                    actionJob.cancelAndJoin()
                    actionJob = launch { action(runningValue) }
                }
            }
        }
    } finally {
        collectorJob.cancelAndJoin()
        updates.close()
        actionJob.cancel()
    }
}

