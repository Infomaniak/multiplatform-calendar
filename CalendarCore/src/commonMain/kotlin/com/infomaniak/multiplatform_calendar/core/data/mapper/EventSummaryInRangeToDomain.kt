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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventSummaryContent
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventSummaryInRange
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventSummary
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventWithOverrides
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventSummaryWithAlarms
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.hasRecurrenceSet
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule

internal fun EventSummaryInRange.toDomain(): EventWithOverrides<EventSummary> {
    val calendarSourceColor = CalendarColors.from(calendarColorArgb).sourceColor
    return EventWithOverrides(
        master = content.toDomain(OccurrenceId.Master(id), calendarSourceColor, accountEmails, rrule, rDates, exDates),
        overridesByOccurrenceKey = overrides.associate { override ->
            override.recurrenceKey to override.content.toDomain(
                occurrenceId = OccurrenceId.Recurrence(id, override.recurrenceKey),
                calendarSourceColor = calendarSourceColor,
                accountEmails = accountEmails,
                isRecurring = true,
            )
        },
    )
}

internal fun EventSummaryInRange.toDomainWithAlarms(): EventWithOverrides<EventSummaryWithAlarms> {
    val summaries = toDomain()
    return EventWithOverrides(
        master = EventSummaryWithAlarms(summaries.master, content.alarms.mapNotNull(AlarmEntity::toDomain)),
        overridesByOccurrenceKey = overrides.associate { override ->
            override.recurrenceKey to EventSummaryWithAlarms(
                summary = summaries.overridesByOccurrenceKey.getValue(override.recurrenceKey),
                alarms = override.content.alarms.mapNotNull(AlarmEntity::toDomain),
            )
        },
    )
}

private fun EventSummaryContent.toDomain(
    occurrenceId: OccurrenceId,
    calendarSourceColor: Int,
    accountEmails: List<String>,
    recurrenceRule: RecurrenceRule? = null,
    rDates: List<IcalDateValue> = emptyList(),
    exDates: List<IcalDateValue> = emptyList(),
    isRecurring: Boolean = hasRecurrenceSet(recurrenceRule, rDates),
): EventSummary = EventSummary(
    occurrenceId = occurrenceId,
    title = summary,
    location = location?.ifBlank { null },
    status = status,
    colors = EventColors.from(colorArgb, calendarSourceColor),
    timing = timing.toDomain(recurrenceRule = recurrenceRule, rDates = rDates, exDates = exDates),
    hasAttendees = attendees.isNotEmpty(),
    hasMeetRoom = !meetRoomUrl.isNullOrBlank(),
    isBookable = !bookableUuid.isNullOrBlank(),
    isRecurring = isRecurring,
    myStatus = attendees.firstOrNull { attendee ->
        accountEmails.any { it.equals(attendee.email, ignoreCase = true) }
    }?.status,
)
