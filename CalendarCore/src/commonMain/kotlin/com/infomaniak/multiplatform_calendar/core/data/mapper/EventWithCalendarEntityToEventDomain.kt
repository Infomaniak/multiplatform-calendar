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
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventSourceColor

internal fun List<EventWithCalendarEntity>.toDomainEvents(): List<Event> {
    val calendarsDomains = mutableMapOf<CalendarId, Calendar>()
    val eventColorsCache = mutableMapOf<EventSourceColor, EventColors>()
    return map { it.toDomainEvent(calendarsDomains, eventColorsCache) }
}

private fun EventWithCalendarEntity.toDomainEvent(
    calendarsDomains: MutableMap<CalendarId, Calendar>,
    eventColorsCache: MutableMap<EventSourceColor, EventColors>,
): Event {
    val calendar = with(calendar) {
        calendarsDomains.getOrPut(id) { toDomain() }
    }

    return event.toDomain(calendar, eventColorsCache)
}

internal fun EventWithCalendarEntity?.toDomainEvent(): Event? = this?.let { event.toDomain(calendar.toDomain()) }
