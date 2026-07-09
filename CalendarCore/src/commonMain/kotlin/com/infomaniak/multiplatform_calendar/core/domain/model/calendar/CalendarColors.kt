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
import com.materialkolor.palettes.TonalPalette

// Every color is computed at the calendar level, even if some are hidden, so computations are optimized and only done once per
// calendar instead of doing it once per event.
public data class CalendarColors(
    val color: Int,
    internal val datavizContainer: ThemedColor,
    internal val onDatavizContainer: ThemedColor,
    val datavizContainerVariant: ThemedColor,
    val onDatavizContainerVariant: ThemedColor,
) {
    public companion object {
        public fun from(color: Int): CalendarColors {
            val palette = TonalPalette.fromInt(color)
            return CalendarColors(
                color = color, // Kept exactly as given
                datavizContainer = palette.themedColor(ThemedColorRole.DatavizContainer),
                onDatavizContainer = palette.themedColor(ThemedColorRole.OnDatavizContainer),
                datavizContainerVariant = palette.themedColor(ThemedColorRole.DatavizContainerVariant),
                onDatavizContainerVariant = palette.themedColor(ThemedColorRole.OnDatavizContainerVariant),
            )
        }
    }
}

private fun TonalPalette.themedColor(tone: ThemedColorRole): ThemedColor = ThemedColor(
    light = tone(tone.light),
    dark = tone(tone.dark),
)

// Tones are based on what the spec uses for dataviz colors which is itself based on the material logic of what tones to pair
private enum class ThemedColorRole(val light: Int, val dark: Int) {
    DatavizContainer(light = 100, dark = 20),
    OnDatavizContainer(light = 40, dark = 80),
    DatavizContainerVariant(light = 90, dark = 30),
    OnDatavizContainerVariant(light = 30, dark = 90),
}
