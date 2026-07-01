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

import com.infomaniak.multiplatform_calendar.core.data.exception.CaldavParsingException
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.remote.model.isICalDateOnly
import com.infomaniak.multiplatform_calendar.core.data.remote.model.isICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseICalDateTime
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseICalDuration
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

@Throws(CaldavParsingException::class)
internal fun RemoteDavEvent.toEntity(calendarId: CalendarId): EventEntity {
    val rawStart = dtstart ?: throw CaldavParsingException("DTSTART is required for event $url")
    val start = parseICalDateTime(rawStart) ?: throw CaldavParsingException("Unparsable DTSTART '$rawStart' for event $url")
    val end = parseICalDateTime(dtend)
    // DTEND and DURATION are mutually exclusive (RFC 5545); keep DURATION only when DTEND is absent.
    val parsedDuration = if (end == null) parseICalDuration(duration) else null
    // A `VALUE=DATE` DTSTART (no time component) denotes a whole-day event (RFC 5545).
    val allDay = isICalDateOnly(rawStart)

    val startTz = resolveTimeZone(allDay, rawStart, dtStartTzid, url, "DTSTART")
    val rawEnd = dtend
    // DTEND can technically carry its own TZID different from DTSTART (RFC 5545 §3.8.2.2).
    // Resolve it independently — but fall back to the start zone when DTEND has no `TZID`
    // attribute and is not UTC, since a bare local DATE-TIME inherits its anchor from DTSTART.
    val endTz = if (rawEnd != null) {
        resolveTimeZone(allDay, rawEnd, dtEndTzid, url, "DTEND") ?: startTz
    } else {
        startTz
    }

    // Anchor zones to materialise wall-clocks into UTC instants for SQL range queries.
    // - All-day is always anchored in UTC so the epoch ms stays device-independent.
    // - Zoned / UTC events use their own zone.
    // - Floating DATE-TIME events (no `TZID`, no `Z`) have NO absolute instant by RFC 5545
    //   semantics — the wall-clock is meant to be reinterpreted in the recipient's current zone at
    //   display time. We store `null` epoch ms for them and let the DAO fall back to a wall-clock
    //   comparison branch, which re-anchors automatically on device zone change (travel, DST).
    val startAnchor = startTz ?: TimeZone.UTC.takeIf { allDay }
    val endAnchor = endTz ?: TimeZone.UTC.takeIf { allDay }

    // Keep DTEND's wall-clock in its own zone (no normalisation): the two zones are stored
    // independently so mixed-zone events (RFC 5545 §3.8.2.2) round-trip losslessly.
    // The DURATION math below uses an arbitrary UTC anchor when the event is fully floating (no
    // DST → the resulting wall-clock is the same regardless of the zone chosen).
    val durationZone = endAnchor ?: TimeZone.UTC
    val effectiveEnd = resolveEffectiveEnd(start, end, parsedDuration, allDay, durationZone)

    return EventEntity(
        id = EventId(url),
        calendarId = calendarId,
        summary = summary ?: "",
        description = description,
        location = location,
        dtStart = start,
        dtEnd = end,
        duration = parsedDuration,
        dtEndEffective = effectiveEnd,
        startTimeZone = startTz?.id,
        endTimeZone = endTz?.id,
        dtStartInstantMs = startAnchor?.let { start.toEpochMs(it) },
        dtEndInstantMs = endAnchor?.let { effectiveEnd.toEpochMs(it) },
        isAllDay = allDay,
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
        attendees = attendees.map { it.toEntity() },
        etag = etag,
        rawIcs = icsData,
    )
}

/**
 * Resolve the [TimeZone] anchoring an iCalendar date/date-time value (RFC 5545):
 * - `VALUE=DATE` (all-day) → `null` (no time-zone applies).
 * - `Z` suffix              → `TimeZone.UTC`.
 * - `TZID` parameter         → `TimeZone.of(tzid)`; throws [CaldavParsingException] if the id is unknown,
 *                              which causes the caller to skip the whole event rather than guess.
 * - otherwise (floating)     → `null` (the recipient supplies its local zone at display time).
 */
private fun resolveTimeZone(
    isAllDay: Boolean,
    rawValue: String,
    tzid: String?,
    eventUrl: String,
    propertyName: String,
): TimeZone? = when {
    isAllDay -> null
    isICalUtcDateTime(rawValue) -> TimeZone.UTC
    tzid != null -> runCatching { TimeZone.of(tzid) }.getOrElse {
        throw CaldavParsingException("Unknown $propertyName TZID '$tzid' for event $eventUrl", it)
    }
    else -> null // Floating: no time-zone anchor (RFC 5545 FORM #1).
}

private fun LocalDateTime.toEpochMs(zone: TimeZone): Long = toInstant(zone).toEpochMilliseconds()

/**
 * Resolve the end used both for range-overlap queries ([EventEntity.dtEndEffective]) and for the domain timing.
 *
 * **Single source of truth** for an event's resolved end (RFC 5545): explicit `DTEND`, else `dtStart + DURATION`,
 * else (whole-day, per RFC 5545 §3.6.1) `dtStart + 1 day`, else `dtStart` (zero-length `DATE-TIME` event).
 *
 * The [endZone] anchors the `DURATION` math; for all-day or floating events any zone works since the
 * computation is purely on the wall-clock.
 */
private fun resolveEffectiveEnd(
    start: LocalDateTime,
    end: LocalDateTime?,
    duration: Duration?,
    allDay: Boolean,
    endZone: TimeZone,
): LocalDateTime = when {
    end != null -> end
    duration != null -> start.toInstant(endZone).plus(duration).toLocalDateTime(endZone)
    allDay -> LocalDateTime(start.date.plus(1, DateTimeUnit.DAY), start.time)
    else -> start
}

