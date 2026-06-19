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
     * Instant-anchored event (`DTSTART;VALUE=DATE-TIME`). Spans `[start, end]`.
     *
     * [end] is the **resolved** end: it already accounts for `DTEND` / `DURATION` (and equals [start] for a
     * zero-length event with neither). The resolution rule lives in a single place — the write-side mapper that
     * persists `EventEntity.dtEndEffective` — so consumers never have to recompute it.
     */
    public data class Timed(
        val start: Instant,
        val end: Instant,
        override val recurrenceRule: RecurrenceRule? = null,
    ) : EventTiming
}

