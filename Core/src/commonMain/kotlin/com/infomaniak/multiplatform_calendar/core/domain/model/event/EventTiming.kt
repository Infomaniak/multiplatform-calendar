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
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * When an event happens.
 *
 * RFC 5545 distinguishes two granularities by the value type of `DTSTART`, which is a structural
 * difference: a `DATE` start is a whole-day event ([AllDay], date-based, no time
 * nor timezone), while a `DATE-TIME` start is anchored to an instant ([Timed]).
 */
public sealed interface EventTiming {

    public val recurrenceRule: RecurrenceRule?

    /**
     * Whole-day event (`DTSTART;VALUE=DATE`). Spans the half-open day range `[startDate, endDate)`.
     *
     * Per RFC 5545 §3.6.1, when no `DTEND`/`DURATION` is given the duration is one day, so [endDate]
     * defaults to the day after [startDate]. [endDate] is exclusive (as iCal `DTEND` is).
     */
    public data class AllDay(
        val startDate: LocalDate,
        val endDate: LocalDate,
        override val recurrenceRule: RecurrenceRule? = null,
    ) : EventTiming

    /**
     * Instant-anchored event (`DTSTART;VALUE=DATE-TIME`). The end is either explicit, a duration, or
     * absent (zero-length) — see [EventEnd] and [resolvedEnd].
     */
    @OptIn(ExperimentalTime::class)
    public data class Timed(
        val start: Instant,
        val end: EventEnd? = null,
        override val recurrenceRule: RecurrenceRule? = null,
    ) : EventTiming {

        /**
         * The effective end [Instant] of a [EventTiming.Timed] event.
         *
         * - [EventEnd.At] → its explicit instant.
         * - [EventEnd.Lasting] → [EventTiming.Timed.start] shifted by the duration.
         * - `null` end → falls back to the start (a zero-length event, per RFC 5545 for a `DATE-TIME` start
         *   without `DTEND`/`DURATION`).
         */
        @OptIn(ExperimentalTime::class)
        public fun resolvedEnd(): Instant = when (val end = end) {
            is EventEnd.At -> end.instant
            is EventEnd.Lasting -> start + end.duration
            null -> start
        }
    }
}

