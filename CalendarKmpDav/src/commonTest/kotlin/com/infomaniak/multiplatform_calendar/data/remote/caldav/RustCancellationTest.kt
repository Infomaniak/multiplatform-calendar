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
package com.infomaniak.multiplatform_calendar.data.remote.caldav

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import uniffi.caldav_bridge.resetTestAwaitForeverCounters
import uniffi.caldav_bridge.testAwaitForever
import uniffi.caldav_bridge.testAwaitForeverDroppedCount
import uniffi.caldav_bridge.testAwaitForeverStartedCount

/**
 * Foundation test proving that cancelling a Kotlin coroutine actually drops the underlying Rust
 * future exported through UniFFI's Tokio async runtime.
 *
 * Simply asserting that `job.join()` returns quickly after `job.cancel()` is insufficient: the
 * Kotlin continuation completes as soon as the coroutine is marked cancelled, even in a hypothetical
 * failure mode where UniFFI abandons the Rust future without dropping it. To close that gap we
 * observe a Rust-side [`DropCounter`] that is incremented from `Drop::drop`, and only consider the
 * cancellation propagated when the drop counter reaches the started counter.
 *
 * Runs on both `./gradlew :CalendarKmpDav:testDebugUnitTest` (JVM + JNA loading the host dylib)
 * and `:iosSimulatorArm64Test` (Kotlin/Native linking the iOS-sim static lib).
 */
class RustCancellationTest {

    @Test
    fun cancellingCoroutine_dropsRustFuture() = runBlocking {
        resetTestAwaitForeverCounters()

        val job = launch { testAwaitForever() }

        // Wait for Rust to actually begin polling the future before cancelling — otherwise we
        // could cancel before the async call ever reached the FFI boundary and prove nothing.
        withTimeout(1.seconds) {
            while (testAwaitForeverStartedCount() == 0u) delay(10.milliseconds)
        }

        job.cancel()
        withTimeout(1.seconds) { job.join() }

        // UniFFI schedules the drop on its own runtime; give it a bounded window to complete.
        withTimeout(1.seconds) {
            while (testAwaitForeverDroppedCount() == 0u) delay(10.milliseconds)
        }

        assertTrue(job.isCancelled, "Job should be marked cancelled")
        assertEquals(1u, testAwaitForeverStartedCount(), "Rust future should have been polled once")
        assertEquals(1u, testAwaitForeverDroppedCount(), "Rust future should have been dropped once")
    }
}
