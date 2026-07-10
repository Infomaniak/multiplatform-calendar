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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtTest {

    @Test
    fun collectRestartingUntilComplete_runsInitialActionAndReturns() = runTest {
        val updates = MutableSharedFlow<Int>(extraBufferCapacity = 4)
        val received = mutableListOf<Int>()

        updates.collectRestartingUntilComplete(
            initialValue = 1,
            shouldRestart = { true },
        ) { value ->
            received += value
        }

        updates.tryEmit(2)
        assertEquals(listOf(1), received)
    }

    @Test
    fun collectRestartingUntilComplete_restartsAndCancelsPreviousActionWhenPredicateMatches() = runTest {
        val updates = MutableSharedFlow<Int>(extraBufferCapacity = 4)
        val startedInitial = CompletableDeferred<Unit>()
        val cancelledInitial = CompletableDeferred<Unit>()
        val restarted = CompletableDeferred<Unit>()

        val job = async {
            updates.collectRestartingUntilComplete(
                initialValue = 1,
                shouldRestart = { newValue -> newValue == 2 },
            ) { value ->
                when (value) {
                    1 -> {
                        startedInitial.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancelledInitial.complete(Unit)
                        }
                    }

                    2 -> restarted.complete(Unit)
                }
            }
        }

        startedInitial.await()
        updates.emit(2)

        restarted.await()
        cancelledInitial.await()
        job.await()
    }

    @Test
    fun collectRestartingUntilComplete_doesNotRestartWhenPredicateIsFalse() = runTest {
        val updates = MutableSharedFlow<Int>(extraBufferCapacity = 4)
        val initialStarted = CompletableDeferred<Unit>()
        val releaseInitial = CompletableDeferred<Unit>()
        var invocationCount = 0

        val job = async {
            updates.collectRestartingUntilComplete(
                initialValue = 1,
                shouldRestart = { false },
            ) {
                invocationCount += 1
                initialStarted.complete(Unit)
                releaseInitial.await()
            }
        }

        initialStarted.await()
        updates.emit(2)
        releaseInitial.complete(Unit)
        job.await()

        assertEquals(1, invocationCount)
    }

    @Test
    fun collectRestartingUntilComplete_ignoresUpdatesAfterCompletion() = runTest {
        val updates = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val received = mutableListOf<Int>()

        val runner = launch {
            updates.collectRestartingUntilComplete(
                initialValue = 10,
                shouldRestart = { true },
            ) { value ->
                received += value
            }
        }

        runner.join()
        updates.tryEmit(11)
        updates.tryEmit(12)

        assertEquals(listOf(10), received)
    }
}

