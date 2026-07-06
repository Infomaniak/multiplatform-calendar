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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette

public data class EventColors(
    val color: Int,
    val onColor: Int,
    val datavizContainer: EventColor,
    val onDatavizContainer: EventColor,
    val datavizContainerVariant: EventColor,
    val onDatavizContainerVariant: EventColor,
) {
    public companion object {
        private const val ON_COLOR_TONE_FOR_DARK_INPUT = 100
        private const val ON_COLOR_TONE_FOR_LIGHT_INPUT = 20

        public fun from(color: Int): EventColors {
            val palette = TonalPalette.fromInt(color)
            return EventColors(
                color = color, // Kept exactly as given
                onColor = palette.onColor(inputTone = Hct.fromInt(color).tone),
                datavizContainer = palette.eventColor(ColorRoleTone.DatavizContainer),
                onDatavizContainer = palette.eventColor(ColorRoleTone.OnDatavizContainer),
                datavizContainerVariant = palette.eventColor(ColorRoleTone.DatavizContainerVariant),
                onDatavizContainerVariant = palette.eventColor(ColorRoleTone.OnDatavizContainerVariant),
            )
        }

        private fun TonalPalette.onColor(inputTone: Double): Int {
            val onTone = if (inputTone < 50) ON_COLOR_TONE_FOR_DARK_INPUT else ON_COLOR_TONE_FOR_LIGHT_INPUT
            return tone(onTone)
        }

        private fun TonalPalette.eventColor(tone: ColorRoleTone): EventColor = EventColor(
            light = tone(tone.light),
            dark = tone(tone.dark),
        )

        // Tones are based on what the spec uses for dataviz colors which is itself based on the material logic of what tones to pair
        private enum class ColorRoleTone(val light: Int, val dark: Int) {
            DatavizContainer(light = 100, dark = 20),
            OnDatavizContainer(light = 40, dark = 80),
            DatavizContainerVariant(light = 90, dark = 30),
            OnDatavizContainerVariant(light = 30, dark = 90),
        }
    }
}
