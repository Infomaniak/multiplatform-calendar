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

internal suspend fun <T> Iterable<T>.forEachParallelLimited(
    parallelism: Int,
    block: suspend (T) -> Unit,
) {
    require(parallelism > 0) { "parallelism must be > 0" }

    val iterator = iterator()
    val mutex = Mutex()

    suspend fun nextOrNull(): T? = mutex.withLock {
        if (iterator.hasNext()) iterator.next() else null
    }

    coroutineScope {
        repeat(parallelism) {
            launch {
                while (isActive) {
                    val item = nextOrNull() ?: break
                    block(item)
                }
            }
        }
    }
}

