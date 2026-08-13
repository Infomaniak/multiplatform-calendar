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
import com.infomaniak.multiplatform_calendar.core.utils.aaToneAgainst
import com.infomaniak.multiplatform_calendar.core.utils.aaaToneAgainst
import com.infomaniak.multiplatform_calendar.core.utils.compositeOver
import com.infomaniak.multiplatform_calendar.core.utils.withAlpha
import com.materialkolor.palettes.TonalPalette

// Every color is computed at the calendar level, even if some are hidden, so computations are optimized and only done once per
// calendar instead of doing it once per event.
public data class CalendarColors(
    val sourceColor: Int,
    val onSourceColor: ThemedColor,
    val sourceVariantColor: Int,
    val onSourceVariantColor: ThemedColor,
) {
    public companion object {
        private const val DEFAULT_COLOR = 0xFF2196F3.toInt() // Material Blue
        private const val LIGHT_SURFACE = 0xFFFFFBFE.toInt()
        private const val DARK_SURFACE = 0xFF141218.toInt()

        internal fun from(calendarColor: CalendarSourceColor?): CalendarColors = from(calendarColor?.argb)

        public fun from(calendarColor: Int?): CalendarColors {
            val sourceColor = calendarColor ?: DEFAULT_COLOR
            val palette = TonalPalette.fromInt(sourceColor)

            // Since the source color will be used for decorative purposes an AA contrast is sufficient.
            val onSourceColor = ThemedColor(
                light = palette.aaToneAgainst(sourceColor),
                dark = palette.aaToneAgainst(sourceColor),
            )

            val sourceVariantColor = sourceColor.withAlpha(0.20f)
            val onSourceVariantColor = ThemedColor(
                light = palette.aaaToneAgainst(sourceVariantColor.compositeOver(LIGHT_SURFACE)),
                dark = palette.aaaToneAgainst(sourceVariantColor.compositeOver(DARK_SURFACE)),
            )

            return CalendarColors(
                sourceColor = sourceColor,
                onSourceColor = onSourceColor,
                sourceVariantColor = sourceVariantColor,
                onSourceVariantColor = onSourceVariantColor,
            )
        }
    }
}
