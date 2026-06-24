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

import com.materialkolor.palettes.TonalPalette

public data class EventColors(
    val datavizContainer: EventColor,
    val onDatavizContainer: EventColor,
    val datavizContainerVariant: EventColor,
    val onDatavizContainerVariant: EventColor,
) {
    public companion object {
        public fun from(color: Int): EventColors {
            val palette = TonalPalette.fromInt(color)
            return EventColors(
                datavizContainer = palette.eventColor(EventColorRole.DatavizContainer),
                onDatavizContainer = palette.eventColor(EventColorRole.OnDatavizContainer),
                datavizContainerVariant = palette.eventColor(EventColorRole.DatavizContainerVariant),
                onDatavizContainerVariant = palette.eventColor(EventColorRole.OnDatavizContainerVariant),
            )
        }

        private fun TonalPalette.eventColor(role: EventColorRole): EventColor = EventColor(
            light = tone(role.light),
            dark = tone(role.dark),
        )

        private enum class EventColorRole(val light: Int, val dark: Int) {
            DatavizContainer(light = 100, dark = 20),
            OnDatavizContainer(light = 40, dark = 80),
            DatavizContainerVariant(light = 90, dark = 30),
            OnDatavizContainerVariant(light = 30, dark = 90),
        }
    }
}
