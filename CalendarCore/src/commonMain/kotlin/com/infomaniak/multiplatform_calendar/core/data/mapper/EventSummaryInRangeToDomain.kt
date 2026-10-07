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

import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventSummaryContent
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventSummaryInRange
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventSummary
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventWithOverrides
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule

internal fun EventSummaryInRange.toDomain(): EventWithOverrides<EventSummary> {
    val calendarSourceColor = CalendarColors.from(calendarColorArgb).sourceColor
    return EventWithOverrides(
        master = content.toDomain(OccurrenceId.Master(id), calendarSourceColor, rrule, rDates, exDates),
        overridesByOccurrenceKey = overrides.associate { override ->
            override.recurrenceKey to override.content.toDomain(
                occurrenceId = OccurrenceId.Recurrence(id, override.recurrenceKey),
                calendarSourceColor = calendarSourceColor,
            )
        },
    )
}

private fun EventSummaryContent.toDomain(
    occurrenceId: OccurrenceId,
    calendarSourceColor: Int,
    recurrenceRule: RecurrenceRule? = null,
    rDates: List<IcalDateValue> = emptyList(),
    exDates: List<IcalDateValue> = emptyList(),
): EventSummary = EventSummary(
    occurrenceId = occurrenceId,
    title = summary,
    location = location?.ifBlank { null },
    status = status,
    colors = EventColors.from(colorArgb, calendarSourceColor),
    timing = timing.toDomain(recurrenceRule = recurrenceRule, rDates = rDates, exDates = exDates),
    hasAttendees = attendees.isNotEmpty(),
)
