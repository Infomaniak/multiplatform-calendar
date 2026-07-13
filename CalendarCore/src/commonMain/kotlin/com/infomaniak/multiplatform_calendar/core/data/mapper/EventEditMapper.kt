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
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toICalLocalDateTime
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteVTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.offsetIn
import kotlinx.datetime.toInstant

internal fun EventEditData.toRemoteEdit(stamp: String): RemoteEventEdit {
    val startZone = timing.startTimeZone
    val endZone = timing.endTimeZone
    return RemoteEventEdit(
        summary = title.ifBlank { null },
        dtStart = timing.start.toICal(timing.isAllDay, startZone),
        dtStartTzid = startZone.tzidForIcal(timing.isAllDay),
        dtEnd = timing.end.toICal(timing.isAllDay, endZone),
        dtEndTzid = endZone.tzidForIcal(timing.isAllDay),
        allDay = timing.isAllDay,
        location = location?.ifBlank { null },
        description = description?.ifBlank { null },
        timeZones = timing.vTimeZones(),
        stamp = stamp,
    )
}

internal fun EventEntity.applyEdit(data: EventEditData, etag: String, rawIcs: String): EventEntity {
    return copy(
        calendarId = data.calendarId,
        summary = data.title,
        location = data.location,
        description = data.description,
        timing = data.timing.toEntity(),
        etag = etag,
        rawIcs = rawIcs,
        isSynced = true,
    )
}

internal fun EventEditData.toNewEntity(
    ref: RemoteDavEventRef,
    rawIcs: String,
): EventEntity {
    return EventEntity(
        id = EventId(ref.url),
        calendarId = calendarId,
        summary = title,
        location = location,
        description = description,
        timing = timing.toEntity(),
        etag = ref.etag,
        rawIcs = rawIcs,
        isSynced = true,
    )
}

/**
 * Serialize a wall-clock [LocalDateTime] as an RFC 5545 value:
 * - All-day      → `DATE` (`YYYYMMDD`).
 * - `zone` UTC   → FORM #2 (`...Z` suffix).
 * - `zone` set   → FORM #3 (no suffix; caller emits a `TZID` parameter alongside).
 * - `zone` null  → FORM #1 floating (no suffix, no `TZID`).
 */
private fun LocalDateTime.toICal(isAllDay: Boolean, zone: TimeZone?): String = when {
    isAllDay -> date.toICalDate()
    zone == TimeZone.UTC -> toInstant(TimeZone.UTC).toICalUtcDateTime()
    else -> toICalLocalDateTime()
}

/** The `TZID` parameter to emit alongside a `DATE-TIME` value, or `null` when none applies. */
private fun TimeZone?.tzidForIcal(isAllDay: Boolean): String? =
    if (isAllDay) null else explicitInIcal()?.id

/**
 * `VTIMEZONE` definitions to embed so the emitted iCalendar references self-contained zones
 * (RFC 5545 §3.6.5). Only FORM #3 (a real IANA `TZID`) needs one; all-day, UTC and floating
 * events emit no `TZID` and therefore need no `VTIMEZONE`.
 *
 * `DTSTART` and `DTEND` can reference different `TZID`s (RFC 5545 §3.8.2.2), so both zones are
 * emitted when distinct. Each offset is sampled at its own wall-clock — a single-offset
 * approximation that resolves this event's wall-clocks correctly everywhere (see [RemoteVTimeZone]).
 */
private fun EventTiming.vTimeZones(): List<RemoteVTimeZone> {
    if (isAllDay) return emptyList()
    val start = startTimeZone.vTimeZone(start)
    val end = endTimeZone.vTimeZone(end)
    return when {
        start == null && end == null -> emptyList()
        start != null && end != null && start.tzid == end.tzid -> listOf(start)
        else -> listOfNotNull(start, end)
    }
}

private fun TimeZone?.vTimeZone(local: LocalDateTime): RemoteVTimeZone? {
    val zone = explicitInIcal() ?: return null
    return RemoteVTimeZone(tzid = zone.id, offset = zone.icalOffsetAt(local))
}

/**
 * This zone if it must be referenced explicitly in iCal (RFC 5545 FORM #3 — a real IANA regional
 * zone), else `null`. Excludes:
 * - `null` (floating FORM #1 or all-day): no `TZID` parameter is emitted.
 * - `TimeZone.UTC` (FORM #2): the `Z` suffix is used instead of a `TZID`.
 *
 * Zones returned here are exactly those that require both a `TZID=` parameter on their
 * DATE-TIME value **and** a matching `VTIMEZONE` block in the emitted iCalendar.
 */
private fun TimeZone?.explicitInIcal(): TimeZone? =
    if (this != null && this != TimeZone.UTC) this else null

/**
 * Format the UTC offset valid at [local] in this zone as an RFC 5545 `TZOFFSETTO` value (e.g. "+0200").
 *
 * Uses [UtcOffset.Formats.FOUR_DIGITS] (always `±HHMM`, never `Z`, sub-minute LMT offsets
 * truncated) which matches RFC 5545 §3.3.14 `utc-offset = ("+" / "-") time-hour time-minute`.
 */
private fun TimeZone.icalOffsetAt(local: LocalDateTime): String =
    local.toInstant(this).offsetIn(this).format(UtcOffset.Formats.FOUR_DIGITS)
