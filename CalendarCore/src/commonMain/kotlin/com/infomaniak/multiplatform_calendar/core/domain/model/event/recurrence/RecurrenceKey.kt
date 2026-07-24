/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.AllDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Floating
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Utc
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Zoned
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Stable identity of a single recurrence instance, derived from the master `DTSTART` value type.
 *
 * RFC 5545 identifies an overridden instance by its `RECURRENCE-ID`, whose value type must match
 * `DTSTART` (§3.8.4.4). Comparing raw epoch UTC would be wrong: two events at the same instant but
 * expressed as floating vs. UTC vs. zoned are *not* the same occurrence. Mirroring the four
 * [EventTiming][com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming] forms
 * keeps the key faithful to the original value type.
 *
 * [canonical] yields a deterministic string suitable for building a synthetic instance id
 * (`masterId + "#" + key.canonical`).
 */
internal sealed class RecurrenceKey {

    /** Deterministic, round-trippable textual form of this key. */
    abstract val canonical: String

    final override fun toString(): String = canonical

    /** `DATE` (whole-day) master: identified by the local date only. */
    data class AllDay(val date: LocalDate) : RecurrenceKey() {
        override val canonical: String get() = "D:$date"
    }

    /** `DATE-TIME` floating master (FORM #1): no zone, wall-clock only. */
    data class Floating(val localDateTime: LocalDateTime) : RecurrenceKey() {
        override val canonical: String get() = "F:$localDateTime"
    }

    /** `DATE-TIME` with `TZID` master (FORM #3): wall-clock paired with its IANA zone. */
    data class Zoned(val localDateTime: LocalDateTime, val timeZoneId: String) : RecurrenceKey() {
        override val canonical: String get() = "Z:$timeZoneId:$localDateTime"
    }

    /** `DATE-TIME` UTC master (FORM #2): identified by the absolute instant. */
    data class Utc(val instant: Instant) : RecurrenceKey() {
        override val canonical: String get() = "U:$instant"
    }
}

/**
 * The [RecurrenceKey] identifying an instance of this master that starts at [localStart] (anchored
 * at [instantStart]), matching the master's `DTSTART` value type.
 */
internal fun EventTiming.recurrenceKeyAt(localStart: LocalDateTime, instantStart: Instant): RecurrenceKey = when {
    isAllDay -> AllDay(localStart.date)
    startTimeZone == TimeZone.UTC -> Utc(instantStart)
    startTimeZone != null -> Zoned(localStart, startTimeZone.id)
    else -> Floating(localStart)
}
