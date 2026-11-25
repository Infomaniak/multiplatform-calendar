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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAtomicApi::class)
class SyncCoordinatorConcurrencyTest {

    @Test
    fun runConflated_concurrentRequests_areConflated() =
        runTest(timeout = 30.seconds) {
            repeat(50) {
                coroutineScope {
                    val coordinator = SyncCoordinator()
                    val callerCount = 16

                    val firstPassEntered = CompletableDeferred<Unit>()
                    val releaseFirstPass = CompletableDeferred<Unit>()
                    val startCallers = CompletableDeferred<Unit>()
                    val allCallersQueued = CompletableDeferred<Unit>()

                    val queued = AtomicInt(0)
                    val calls = AtomicInt(0)
                    val active = AtomicInt(0)
                    val parallelExecution = AtomicBoolean(false)

                    val syncAction: suspend () -> SyncResult = {
                        if (active.incrementAndFetch() != 1) {
                            parallelExecution.store(true)
                        }

                        try {
                            if (calls.incrementAndFetch() == 1) {
                                firstPassEntered.complete(Unit)
                                releaseFirstPass.await()
                                SyncResult.NeedsResync
                            } else {
                                SyncResult.Completed
                            }
                        } finally {
                            active.decrementAndFetch()
                        }
                    }

                    val first = launch(Dispatchers.Default) {
                        coordinator.runConflated(syncAction)
                    }

                    firstPassEntered.await()

                    val callers = List(callerCount) {
                        launch(Dispatchers.Default) {
                            startCallers.await()

                            val request = launch(start = CoroutineStart.UNDISPATCHED) {
                                coordinator.runConflated(syncAction)
                            }

                            if (queued.incrementAndFetch() == callerCount) {
                                allCallersQueued.complete(Unit)
                            }

                            request.join()
                        }
                    }

                    try {
                        startCallers.complete(Unit)
                        allCallersQueued.await()
                    } finally {
                        releaseFirstPass.complete(Unit)
                    }

                    callers.joinAll()
                    first.join()

                    assertFalse(parallelExecution.load(), "Sync actions ran simultaneously")
                    assertEquals(2, calls.load(), "Requests must share one follow-up pass")
                }
            }
        }
}
