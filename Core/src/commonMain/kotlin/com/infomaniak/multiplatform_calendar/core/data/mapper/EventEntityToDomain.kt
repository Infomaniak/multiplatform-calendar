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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun EventEntity.toDomain(): Event {
    val timeZone = TimeZone.currentSystemDefault()
    // Attendees, organizer and recurrence rule are not persisted yet; they are left to defaults.
    val start = dtStart?.toInstant(timeZone) ?: Instant.DISTANT_PAST
    return Event(
        id = id,
        calendarId = calendarId,
        title = summary,
        description = description,
        location = location,
        start = start,
        end = dtEnd?.toInstant(timeZone) ?: start,
        isAllDay = isAllDay,
        etag = etag,
        rawIcs = rawIcs,
        lastModified = lastModified?.toInstant(timeZone),
        isSynced = isSynced,
    )
}
