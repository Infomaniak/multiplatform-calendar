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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.remote.model.isICalDateOnly
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseICalDateTime
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseICalDuration
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent

internal fun RemoteDavEvent.toEntity(calendarId: CalendarId): EventEntity? {
    val start = parseICalDateTime(dtstart) ?: return null
    val end = parseICalDateTime(dtend)
    return EventEntity(
        id = EventId(url),
        calendarId = calendarId,
        summary = summary ?: "",
        description = description,
        location = location,
        dtStart = start,
        dtEnd = end,
        // DTEND and DURATION are mutually exclusive (RFC 5545); keep DURATION only when DTEND is absent.
        duration = if (end == null) parseICalDuration(duration) else null,
        // A `VALUE=DATE` DTSTART (no time component) denotes a whole-day event (RFC 5545).
        isAllDay = isICalDateOnly(dtstart),
        created = parseICalDateTime(created),
        lastModified = parseICalDateTime(lastModified),
        dtStamp = parseICalDateTime(dtstamp),
        rrule = rrule,
        status = status,
        transp = transp,
        classification = classification,
        priority = priority?.toIntOrNull(),
        sequence = sequence?.toIntOrNull(),
        categories = categories,
        organizer = organizer,
        etag = etag,
        rawIcs = icsData,
    )
}
