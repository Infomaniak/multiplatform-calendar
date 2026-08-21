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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal fun EventEntity.toDomain(
    calendar: Calendar,
): Event = content.toDomain(
    masterEventId = id,
    occurrenceId = OccurrenceId(id.url),
    calendar = calendar,
    recurrenceRule = rrule,
    rDates = rDates,
    exDates = exDates,
)

/**
 * Shared with [EventOverrideEntity][com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity]:
 * an override redefines the very same properties, only its identity and recurrence differ.
 */
internal fun EventContentEntity.toDomain(
    masterEventId: EventId,
    occurrenceId: OccurrenceId,
    calendar: Calendar,
    recurrenceRule: RecurrenceRule? = null,
    rDates: List<IcalDateValue> = emptyList(),
    exDates: List<IcalDateValue> = emptyList(),
): Event {
    val organizer = organizer?.toDomain()
    val attendees = attendees.map { it.toDomain(isOrganizer = it.email == organizer?.email) }
    return Event(
        masterEventId = masterEventId,
        occurrenceId = occurrenceId,
        calendarId = calendar.id,
        accountId = calendar.accountId,
        title = summary,
        description = description?.ifBlank { null },
        location = location?.ifBlank { null },
        status = status,
        classification = classification,
        categories = categories?.filter { it.isNotBlank() }.orEmpty(),
        timing = timing.toDomain(recurrenceRule = recurrenceRule, rDates = rDates, exDates = exDates),
        lastModified = lastModified?.toInstant(TimeZone.UTC),
        attendees = attendees,
        organizer = organizer,
        colors = EventColors.from(colorArgb, calendar.colors.sourceColor),
        canEdit = calendar.accessLevel.canWrite,
        alarms = alarms.mapNotNull(AlarmEntity::toDomain),
    )
}
