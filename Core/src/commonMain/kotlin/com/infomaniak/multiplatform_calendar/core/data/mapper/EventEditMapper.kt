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
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toICalDate
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun EventEditData.toRemoteEdit(stamp: String): RemoteEventEdit = when (val timing = timing) {
    is EventTiming.AllDay -> RemoteEventEdit(
        summary = title.ifBlank { null },
        dtStart = timing.startDate.toICalDate(),
        dtEnd = timing.endDate.toICalDate(),
        allDay = true,
        location = location?.ifBlank { null },
        description = description?.ifBlank { null },
        stamp = stamp,
    )

    is EventTiming.Timed -> RemoteEventEdit(
        summary = title.ifBlank { null },
        dtStart = timing.start.toICalUtcDateTime(),
        dtEnd = timing.end.toICalUtcDateTime(),
        allDay = false,
        location = location?.ifBlank { null },
        description = description?.ifBlank { null },
        stamp = stamp,
    )
}

internal fun EventEntity.applyEdit(data: EventEditData, etag: String, rawIcs: String): EventEntity {
    val end = data.timing.entityEnd()
    return copy(
        calendarId = data.calendarId,
        summary = data.title,
        location = data.location,
        description = data.description,
        dtStart = data.timing.entityStart(),
        dtEnd = end,
        dtEndEffective = end,
        isAllDay = data.timing is EventTiming.AllDay,
        etag = etag,
        rawIcs = rawIcs,
        isSynced = true,
    )

@OptIn(ExperimentalTime::class)
internal fun EventEditData.toNewEntity(
    eventId: EventId,
    etag: String,
    rawIcs: String,
): EventEntity {
    val end = timing.entityEnd()
    return EventEntity(
        id = eventId,
        calendarId = calendarId,
        summary = title,
        location = location,
        description = description,
        dtStart = timing.entityStart(),
        dtEnd = end,
        dtEndEffective = end,
        duration = null,
        isAllDay = timing is EventTiming.AllDay,
        etag = etag,
        rawIcs = rawIcs,
        isSynced = true,
    )
}

@OptIn(ExperimentalTime::class)
private fun EventTiming.entityStart(): LocalDateTime = when (this) {
    is EventTiming.AllDay -> LocalDateTime(startDate, LocalTime(0, 0))
    is EventTiming.Timed -> start.toLocalDateTime(TimeZone.UTC)
}

private fun EventTiming.entityEnd(): LocalDateTime = when (this) {
    is EventTiming.AllDay -> LocalDateTime(endDate, LocalTime(0, 0))
    is EventTiming.Timed -> end.toLocalDateTime(TimeZone.UTC)
}
