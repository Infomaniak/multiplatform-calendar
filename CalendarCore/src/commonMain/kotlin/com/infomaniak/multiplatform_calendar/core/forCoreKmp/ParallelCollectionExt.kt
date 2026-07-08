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

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmInline

/**
 * Executes [block] for each element with a maximum number of concurrent workers.
 *
 * - If this function completes successfully, each element is processed exactly once.
 * - Processing order is not guaranteed when [limit] > 1.
 * - If [block] fails for one element, the exception is rethrown, sibling workers are cancelled,
 *   and remaining elements may not be processed.
 * - Cancellation of the caller cancels all workers.
 *
 * When this iterable is a [Collection], worker count is capped to [limit] and [Collection.size]
 * to avoid spawning unnecessary coroutines.
 *
 * @param limit Maximum number of concurrent workers. Must be strictly positive.
 * @param block Suspended work executed for each element.
 * @throws IllegalArgumentException when [limit] <= 0.
 */
internal suspend fun <T> Iterable<T>.forEachParallelLimited(
    limit: Int,
    block: suspend (T) -> Unit,
) {
    require(limit > 0) { "parallelism must be > 0" }

    val iterator = iterator()
    val mutex = Mutex()

    suspend fun nextOrNull(): Item<T>? = mutex.withLock {
        if (iterator.hasNext()) Item(iterator.next()) else null
    }

    val workerCount = when (this) {
        is Collection<*> -> minOf(limit, size)
        else -> limit
    }

    coroutineScope {
        repeat(workerCount) {
            launch {
                while (isActive) {
                    val item = nextOrNull() ?: break
                    block(item.value)
                }
            }
        }
    }
}

@JvmInline
private value class Item<T>(val value: T)

