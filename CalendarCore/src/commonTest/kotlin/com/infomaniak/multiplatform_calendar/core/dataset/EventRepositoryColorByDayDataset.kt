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
package com.infomaniak.multiplatform_calendar.core.dataset

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import kotlinx.datetime.LocalDateTime

internal data class TimedEventSeed(
    val id: EventId,
    val calendarId: CalendarId,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

internal object EventRepositoryColorByDayDataset {

    /**
     * Two calendars on day 15 (with a duplicate calendar A event) and one calendar A event on day 16.
     */
    fun groupingScenario(
        calendarA: CalendarId,
        calendarB: CalendarId,
    ): List<TimedEventSeed> = listOf(
        TimedEventSeed(
            id = EventId("event://a1"),
            calendarId = calendarA,
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 11, 0),
        ),
        TimedEventSeed(
            id = EventId("event://a2"),
            calendarId = calendarA,
            start = LocalDateTime(2026, 6, 15, 15, 0),
            end = LocalDateTime(2026, 6, 15, 16, 0),
        ),
        TimedEventSeed(
            id = EventId("event://b1"),
            calendarId = calendarB,
            start = LocalDateTime(2026, 6, 15, 12, 0),
            end = LocalDateTime(2026, 6, 15, 13, 0),
        ),
        TimedEventSeed(
            id = EventId("event://a3"),
            calendarId = calendarA,
            start = LocalDateTime(2026, 6, 16, 9, 0),
            end = LocalDateTime(2026, 6, 16, 10, 0),
        ),
    )
}

