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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun EventEditData.toRemoteEdit(stamp: String): RemoteEventEdit = RemoteEventEdit(
    summary = title.ifBlank { null },
    dtStart = timing.start.toICal(timing.isAllDay),
    dtEnd = timing.end.toICal(timing.isAllDay),
    allDay = timing.isAllDay,
    location = location?.ifBlank { null },
    description = description?.ifBlank { null },
    stamp = stamp,
)

internal fun EventEntity.applyEdit(data: EventEditData, etag: String, rawIcs: String): EventEntity {
    val end = data.timing.end.toLocalDateTime(TimeZone.UTC)
    return copy(
        calendarId = data.calendarId,
        summary = data.title,
        location = data.location,
        description = data.description,
        dtStart = data.timing.start.toLocalDateTime(TimeZone.UTC),
        dtEnd = end,
        dtEndEffective = end,
        isAllDay = data.timing.isAllDay,
        etag = etag,
        rawIcs = rawIcs,
        isSynced = true,
    )
}

internal fun EventEditData.toNewEntity(
    ref: RemoteDavEventRef,
    rawIcs: String,
): EventEntity {
    val end = timing.end.toLocalDateTime(TimeZone.UTC)
    return EventEntity(
        id = EventId(ref.url),
        calendarId = calendarId,
        summary = title,
        location = location,
        description = description,
        dtStart = timing.start.toLocalDateTime(TimeZone.UTC),
        dtEnd = end,
        dtEndEffective = end,
        isAllDay = timing.isAllDay,
        etag = ref.etag,
        rawIcs = rawIcs,
        isSynced = true,
    )
}

private fun Instant.toICal(isAllDay: Boolean): String =
    if (isAllDay) toLocalDateTime(TimeZone.UTC).date.toICalDate() else toICalUtcDateTime()
