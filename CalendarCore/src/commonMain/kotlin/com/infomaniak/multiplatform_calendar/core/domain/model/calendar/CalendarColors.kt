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

import com.materialkolor.palettes.TonalPalette

internal object CalendarColors {
    private const val LIGHT_DARK_THRESHOLD = 50
    private const val ON_COLOR_TONE_FOR_DARK_INPUT = 100
    private const val ON_COLOR_TONE_FOR_LIGHT_INPUT = 20

    internal fun TonalPalette.onColor(inputTone: Double): Int {
        val onTone = if (inputTone < LIGHT_DARK_THRESHOLD) ON_COLOR_TONE_FOR_DARK_INPUT else ON_COLOR_TONE_FOR_LIGHT_INPUT
        return tone(onTone)
    }
}
