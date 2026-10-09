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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventSummaryWithAlarms
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.hasRecurrenceSet

/** This event as listed for a user with the given [accountEmails]. */
internal fun Event.toSummary(accountEmails: List<String> = listOf("me@example.com")): EventSummary = EventSummary(
    occurrenceId = occurrenceId,
    title = title,
    location = location,
    status = status,
    colors = colors,
    timing = timing,
    hasAttendees = attendees.isNotEmpty(),
    hasMeetRoom = !meetRoomUrl.isNullOrBlank(),
    isBookable = !bookableUuid.isNullOrBlank(),
    isRecurring = isOccurrence || timing.hasRecurrenceSet(),
    myStatus = attendees.firstOrNull { attendee ->
        accountEmails.any { it.equals(attendee.email, ignoreCase = true) }
    }?.status,
)

internal fun Event.toAlarmedEvent(): EventSummaryWithAlarms = EventSummaryWithAlarms(toSummary(), alarms)
