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

public data class EventColors(
    val eventSourceColor: EventSourceColor,
    val sourceColor: Int,
    val onSourceColor: ThemedColor,
    val sourceVariantColor: Int,
    val onSourceVariantColor: ThemedColor,
    public companion object {
        public fun from(color: CalendarColors): EventColors = EventColors(
            eventSourceColor = EventSourceColor(color.calendarSourceColor), //TODO(gigi): Why it was null before?
            sourceColor = color.sourceColor,
            onSourceColor = color.onSourceColor,
            sourceVariantColor = color.sourceVariantColor,
            onSourceVariantColor = color.onSourceVariantColor,
        )

        /** The [cache] must be reused across a batch to avoid recomputing the palette for shared source colors. */
        internal fun from(
            eventSourceColor: EventSourceColor,
            cache: MutableMap<EventSourceColor, EventColors>,
        ): EventColors = cache.getOrPut(eventSourceColor) {
            val calendarColors = CalendarColors.from(eventSourceColor.argb)
            EventColors(
                eventSourceColor = eventSourceColor,
                sourceColor = calendarColors.sourceColor,
                onSourceColor = calendarColors.onSourceColor,
                sourceVariantColor = calendarColors.sourceVariantColor,
                onSourceVariantColor = calendarColors.onSourceVariantColor,
            )
        }
    }
}
