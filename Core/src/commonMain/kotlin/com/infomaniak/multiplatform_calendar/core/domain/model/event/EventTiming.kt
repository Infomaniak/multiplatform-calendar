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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * When an event happens.
 *
 * RFC 5545 distinguishes two value types on `DTSTART`: `DATE` (whole-day, no time / timezone) and
 * `DATE-TIME` (anchored instant). To keep a single shape that maps cleanly to `Foundation.Date` on iOS
 * (which has no date-only type), both are represented as [Instant] pairs and discriminated by [isAllDay].
 *
 * Invariants when [isAllDay] is `true`:
 *  - [start] and [end] sit on midnight **UTC** of their respective dates;
 *  - [end] is the exclusive day after the last covered day (so a single-day event has `end = start + 1d`,
 *    matching iCal `DTEND;VALUE=DATE` semantics).
 *
 * TODO: Timezones are not parsed yet — every `Instant` is currently assumed to be UTC. The
 * "floating" iCal mode (no `TZID`, no `Z`) is not supported either; events with floating times are
 * treated as if anchored UTC. Add a `timeZone` field (and possibly an `isFloating` flag) when the
 * CalDAV parser starts producing real `TZID` values.
 */
public data class EventTiming(
    val start: Instant,
    val end: Instant,
    val isAllDay: Boolean,
    val recurrenceRule: RecurrenceRule? = null,
) {
    init {
        require(end >= start) { "EventTiming.end must be >= start ($start..$end)" }
        if (isAllDay) {
            val startLocal = start.toLocalDateTime(TimeZone.UTC)
            val endLocal = end.toLocalDateTime(TimeZone.UTC)
            require(startLocal.time == LocalTime(0, 0) && endLocal.time == LocalTime(0, 0)) {
                "AllDay timing must sit on midnight UTC (got start=$start, end=$end)"
            }
        }
    }
}
