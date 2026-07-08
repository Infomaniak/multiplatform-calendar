/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

/**
 * Position of a day within the multi-day span it belongs to (e.g. day 2 of a 5-day event).
 *
 * [index] is 0-based and [count] is the total number of days the span covers. Both are **absolute to
 * the span** (not to any visible window). Used by [EventDaySlice] so callers get first/last-day
 * information and a ready-to-display label from a single cohesive value.
 */
public data class DaySpanPosition(
    val index: Int,
    val count: Int,
) {
    init {
        require(count >= 1) { "count must be >= 1, was $count" }
        require(index in 0 until count) { "index $index is out of bounds for count $count" }
    }

    /** 1-based number of this day within the span, for display (e.g. day `1` of [count]). */
    val number: Int get() = index + 1
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == count - 1
    /** `true` when the span covers a single day (both first and last). */
    val isSingleDay: Boolean get() = count == 1

    /** Human-readable label such as `"1/23"`. */
    override fun toString(): String = "$number/$count"
}
