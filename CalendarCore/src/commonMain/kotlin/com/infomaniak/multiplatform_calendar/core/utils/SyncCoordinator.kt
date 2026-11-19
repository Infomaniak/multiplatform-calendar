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
package com.infomaniak.multiplatform_calendar.core.utils

import com.infomaniak.multiplatform_calendar.core.extensions.SyncResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(AppScope::class)
@Inject
internal class SyncCoordinator {

    private val executionMutex = Mutex()
    private val stateMutex = Mutex()

    private var latestRequestId = 0L
    private var lastCompletedRequestId = 0L

    suspend fun runConflated(syncAction: suspend () -> SyncResult) {
        stateMutex.withLock {
            latestRequestId++
        }

        executionMutex.withLock {
            while (true) {
                currentCoroutineContext().ensureActive()
                val batchId = stateMutex.withLock {
                    latestRequestId.takeIf { it > lastCompletedRequestId }
                } ?: break

                val result = syncAction()

                stateMutex.withLock {
                    lastCompletedRequestId = batchId

                    if (result == SyncResult.NeedsResync && latestRequestId == batchId) {
                        latestRequestId++
                    }
                }
            }
        }
    }
}

