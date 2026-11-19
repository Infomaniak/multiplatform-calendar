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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncCoordinatorTest {

    @Test
    fun runConflated_singleCompletedRequest_runsExactlyOnce() = runTest {
        val coordinator = SyncCoordinator()
        var calls = 0

        coordinator.runConflated {
            calls += 1
            SyncResult.Completed
        }

        assertEquals(1, calls)
    }

    @Test
    fun runConflated_needsResyncWithoutConcurrentRequest_runsOneExtraPass() = runTest {
        val coordinator = SyncCoordinator()
        var calls = 0

        coordinator.runConflated {
            calls += 1
            if (calls == 1) SyncResult.NeedsResync else SyncResult.Completed
        }

        assertEquals(2, calls)
    }

    @Test
    fun runConflated_manyOverlappingRequests_areConflatedIntoSingleAdditionalPass() = runTest {
        val coordinator = SyncCoordinator()
        val totalRequests = 16
        val allCoroutinesStarted = CompletableDeferred<Unit>()
        val firstPassEntered = CompletableDeferred<Unit>()
        var started = 0
        var calls = 0

        val jobs = List(totalRequests) {
            launch {
                started += 1
                if (started == totalRequests) allCoroutinesStarted.complete(Unit)
                coordinator.runConflated {
                    calls += 1
                    if (calls == 1) {
                        firstPassEntered.complete(Unit)
                        allCoroutinesStarted.await()
                        repeat(3) { yield() }
                    }
                    SyncResult.Completed
                }
            }
        }

        firstPassEntered.await()
        jobs.joinAll()

        assertEquals(2, calls)
    }

    @Test
    fun runConflated_overlappingCallers_neverExecuteSyncActionInParallel() = runTest {
        val coordinator = SyncCoordinator()
        val lock = Mutex()
        var active = 0
        var maxActive = 0

        val jobs = List(10) {
            launch {
                coordinator.runConflated {
                    lock.withLock {
                        active += 1
                        if (active > maxActive) maxActive = active
                    }
                    repeat(5) { yield() }
                    lock.withLock {
                        active -= 1
                    }
                    SyncResult.Completed
                }
            }
        }

        jobs.joinAll()

        assertEquals(1, maxActive)
    }

    @Test
    fun runConflated_concurrentRequestAlreadyQueued_needsResyncDoesNotScheduleThirdPass() = runTest {
        val coordinator = SyncCoordinator()
        val firstPassEntered = CompletableDeferred<Unit>()
        val releaseFirstPass = CompletableDeferred<Unit>()
        var calls = 0

        val first = launch {
            coordinator.runConflated {
                calls += 1
                if (calls == 1) {
                    firstPassEntered.complete(Unit)
                    releaseFirstPass.await()
                    SyncResult.NeedsResync
                } else {
                    SyncResult.Completed
                }
            }
        }

        firstPassEntered.await()
        val second = launch {
            coordinator.runConflated { SyncResult.Completed }
        }
        repeat(3) { yield() }
        releaseFirstPass.complete(Unit)

        first.join()
        second.join()

        assertEquals(2, calls)
    }

    @Test
    fun runConflated_failurePropagatesAndPendingWorkIsProcessedByNextCaller() = runTest {
        val coordinator = SyncCoordinator()
        val firstPassEntered = CompletableDeferred<Unit>()
        val releaseFailure = CompletableDeferred<Unit>()
        var firstActionCalls = 0
        var secondActionCalls = 0

        val first = launch {
            assertFailsWith<IllegalStateException> {
                coordinator.runConflated {
                    firstActionCalls += 1
                    if (firstActionCalls == 1) {
                        firstPassEntered.complete(Unit)
                        releaseFailure.await()
                        throw IllegalStateException("boom")
                    }
                    SyncResult.Completed
                }
            }
        }

        firstPassEntered.await()
        val second = launch {
            coordinator.runConflated {
                secondActionCalls += 1
                SyncResult.Completed
            }
        }
        repeat(3) { yield() }
        releaseFailure.complete(Unit)

        first.join()
        second.join()

        assertEquals(1, firstActionCalls)
        assertEquals(1, secondActionCalls)
    }

    @Test
    fun runConflated_cancellationStopsCurrentPassAndNextCallerCanProceed() = runTest {
        val coordinator = SyncCoordinator()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        var nextCallCount = 0

        val job = launch {
            coordinator.runConflated {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
                SyncResult.Completed
            }
        }

        started.await()
        job.cancelAndJoin()
        assertTrue(cancelled.isCompleted)

        coordinator.runConflated {
            nextCallCount += 1
            SyncResult.Completed
        }

        assertEquals(1, nextCallCount)
    }
}
