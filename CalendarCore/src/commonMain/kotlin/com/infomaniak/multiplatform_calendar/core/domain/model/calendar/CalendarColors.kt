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
package com.infomaniak.multiplatform_calendar.core.domain.model.calendar

import com.infomaniak.multiplatform_calendar.core.domain.model.event.ThemedColor
import com.infomaniak.multiplatform_calendar.core.utils.ColorComputation

// Every color is computed at the calendar level, even if some are hidden, so computations are optimized and only done once per
// calendar instead of doing it once per event.
public data class CalendarColors(
    val sourceColor: Int,
    val onSourceColor: ThemedColor,
    internal val containerColor: Int,
    internal val onContainerColor: ThemedColor,
    internal val containerVariantColor: Int,
    internal val onContainerVariantColor: ThemedColor,
) {
    public companion object {
        private const val DEFAULT_COLOR = 0xFF2196F3.toInt() // Material Blue

        internal fun from(calendarColor: CalendarSourceColor?): CalendarColors = from(calendarColor?.argb)

        public fun from(calendarColor: Int?): CalendarColors {
            val sourceColor = calendarColor ?: DEFAULT_COLOR
            val calendarColors = ColorComputation.from(sourceColor)

            return CalendarColors(
                sourceColor = sourceColor,
                onSourceColor = calendarColors.onSourceColor,
                containerColor = calendarColors.containerColor,
                onContainerColor = calendarColors.onContainerColor,
                containerVariantColor = calendarColors.containerVariantColor,
                onContainerVariantColor = calendarColors.onContainerVariantColor,
            )
        }
    }
}
