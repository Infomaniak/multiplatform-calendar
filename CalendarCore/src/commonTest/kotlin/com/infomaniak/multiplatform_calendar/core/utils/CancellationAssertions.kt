/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2025-2026 Infomaniak Network SA
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

import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith

/**
 * Asserts that [block] throws [CancellationException] when invoked with an already-cancelled coroutine context.
 *
 * Use this to verify that a `suspend` function cooperates with cancellation (via `ensureActive()`, `yield()`, …).
 *
 * The shape is deliberate — simpler variants silently mislead:
 *  - cancelling directly in `runTest` (no `coroutineScope`) cancels the test's own Job → `runTest` fails.
 *  - wrapping the whole `coroutineScope` in `assertFailsWith` is a false positive: the scope re-raises
 *    [CancellationException] on cancelled exit, so the assertion passes even when [block] never throws.
 *  - catching with `runCatching` instead of a typed `catch` swallows the [AssertionError] fired by
 *    `assertFailsWith` when the block returns normally → false positive again.
 */
suspend inline fun assertCancels(crossinline block: suspend () -> Any) {
    try {
        coroutineScope {
            currentCoroutineContext().cancel()
            assertFailsWith<CancellationException> {
                block()
            }
        }
    } catch (_: CancellationException) {
        // coroutineScope re-raises on cancelled exit; the meaningful check happens inside assertFailsWith.
    }
}
