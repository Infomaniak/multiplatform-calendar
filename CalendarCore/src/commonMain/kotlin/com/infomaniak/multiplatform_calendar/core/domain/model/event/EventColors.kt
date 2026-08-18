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

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSourceColor
import com.infomaniak.multiplatform_calendar.core.utils.ColorComputation

public data class EventColors(
    val calendarSourceColor: CalendarSourceColor,
    val sourceColor: Int,
    val containerColor: Int,
    val onContainerColor: ThemedColor,
    val containerVariantColor: Int,
    val onContainerVariantColor: ThemedColor,
) {
    public companion object {
        public fun from(color: CalendarColors): EventColors = EventColors(
            calendarSourceColor = CalendarSourceColor(color.sourceColor),
            sourceColor = color.sourceColor,
            containerColor = color.containerColor,
            onContainerColor = color.onContainerColor,
            containerVariantColor = color.containerVariantColor,
            onContainerVariantColor = color.onContainerVariantColor,
        )

        /** The [cache] must be reused across a batch to avoid recomputing the palette for shared source colors. */
        internal fun from(
            eventSourceColor: EventSourceColor,
            calendarSourceColor: Int,
            cache: MutableMap<EventSourceColor, EventColors>,
        ): EventColors = cache.getOrPut(eventSourceColor) {
            val eventColors = ColorComputation.from(eventSourceColor.argb)
            EventColors(
                calendarSourceColor = CalendarSourceColor(calendarSourceColor),
                sourceColor = eventSourceColor.argb,
                containerColor = eventColors.containerColor,
                onContainerColor = eventColors.onContainerColor,
                containerVariantColor = eventColors.containerVariantColor,
                onContainerVariantColor = eventColors.onContainerVariantColor,
            )
        }
    }
}
