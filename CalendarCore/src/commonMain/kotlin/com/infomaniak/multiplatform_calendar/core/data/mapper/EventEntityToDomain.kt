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
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventImpl
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

// TODO: Timezones are not handled yet — we assume UTC when converting to Instant.
private fun LocalDateTime.toUtcInstant(): Instant = toInstant(TimeZone.UTC)

internal fun EventEntity.toDomain(calendar: Calendar, eventColors: EventColors): Event = EventImpl(
    id = id,
    calendarId = calendarId,
    accountId = calendar.accountId,
    title = summary,
    description = description,
    location = location,
    status = status,
    categories = categories,
    timing = toTiming(),
    lastModified = lastModified?.toUtcInstant(),
    calendarColor = calendar.color,
    colors = eventColors,
    canEdit = calendar.accessLevel.canWrite,
)

private fun EventEntity.toTiming(): EventTiming = EventTiming(
    start = dtStart.toUtcInstant(),
    // dtEndEffective already resolves DTEND/DURATION (and defaults to +1 day for AllDay).
    end = dtEndEffective.toUtcInstant(),
    isAllDay = isAllDay,
    recurrenceRule = null, // TODO: Parse rrule string to RecurrenceRule
)
