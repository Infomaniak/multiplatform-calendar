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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import kotlin.collections.getOrPut

internal fun List<EventWithCalendarEntity>.toDomainEvents(): List<Event> {
    val calendarsDomains = mutableMapOf<CalendarId, Calendar>()
    val calendarColors = mutableMapOf<CalendarColor, EventColors>()
    return map { it.toDomainEvent(calendarsDomains, calendarColors) }
}

private fun EventWithCalendarEntity.toDomainEvent(
    calendarsDomains: MutableMap<CalendarId, Calendar>,
    calendarColors: MutableMap<CalendarColor, EventColors>,
): Event {
    val calendar = with(calendar) {
        calendarsDomains.getOrPut(id) { toDomain() }
    }

    val eventColors = calendarColors.getOrPut(calendar.color, calendar::eventColors)

    return event.toDomain(calendar, eventColors)
}

internal fun EventWithCalendarEntity?.toDomainEvent(): Event? {
    return this?.let {
        val calendar = calendar.toDomain()
        event.toDomain(calendar, calendar.eventColors())
    }
}

private fun Calendar.eventColors(): EventColors = EventColors.from(color.argb)
