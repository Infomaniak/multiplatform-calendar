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
package com.infomaniak.multiplatform_calendar.core.forCoreKmp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ParallelCollectionExtTest {

    @Test
    fun forEachParallelLimited_parallelismOne_preservesIterationOrder() = runTest {
        val processed = mutableListOf<Int>()

        (1..5).asIterable().forEachParallelLimited(parallelism = 1) { item ->
            processed += item
        }

        assertEquals(listOf(1, 2, 3, 4, 5), processed)
    }

    @Test
    fun forEachParallelLimited_emptyIterable_doesNotInvokeBlock() = runTest {
        var calls = 0

        emptyList<Int>().forEachParallelLimited(parallelism = 3) {
            calls += 1
        }

        assertEquals(0, calls)
    }

    @Test
    fun forEachParallelLimited_parallelismGreaterThanSize_processesEachElementOnce() = runTest {
        val values = (1..5).toList()
        val seen = mutableSetOf<Int>()
        val seenLock = Mutex()

        values.forEachParallelLimited(parallelism = 32) { item ->
            seenLock.withLock {
                seen += item
            }
        }

        assertEquals(values.toSet(), seen)
    }

    @Test
    fun forEachParallelLimited_invalidParallelism_throws() = runTest {
        val zero = assertFailsWith<IllegalArgumentException> {
            listOf(1).forEachParallelLimited(parallelism = 0) { }
        }
        assertEquals("parallelism must be > 0", zero.message)

        val negative = assertFailsWith<IllegalArgumentException> {
            listOf(1).forEachParallelLimited(parallelism = -2) { }
        }
        assertEquals("parallelism must be > 0", negative.message)
    }

    @Test
    fun forEachParallelLimited_blockFailure_propagatesAndCancelsSiblings() = runTest {
        val started = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()

        val thrown = assertFailsWith<IllegalStateException> {
            listOf(1, 2).forEachParallelLimited(parallelism = 2) { item ->
                if (item == 1) {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        canceled.complete(Unit)
                    }
                }
                if (item == 2) {
                    started.await()
                    throw IllegalStateException("boom")
                }
                currentCoroutineContext().ensureActive()
            }
        }

        assertEquals("boom", thrown.message)
        assertTrue(canceled.isCompleted)
    }

    @Test
    fun forEachParallelLimited_neverExceedsRequestedParallelism() = runTest {
        var active = 0
        var maxActive = 0
        val lock = Mutex()

        (1..30).asIterable().forEachParallelLimited(parallelism = 4) {
            lock.withLock {
                active += 1
                if (active > maxActive) maxActive = active
            }
            delay(10)
            lock.withLock {
                active -= 1
            }
        }

        assertTrue(maxActive <= 4)
        assertTrue(maxActive >= 1)
    }

    @Test
    fun forEachParallelLimited_parentCancellation_stopsWorkers() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()

        val job: Job = launch {
            (1..100).asIterable().forEachParallelLimited(parallelism = 4) {
                started.complete(Unit)
                gate.await()
            }
        }

        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }
}


