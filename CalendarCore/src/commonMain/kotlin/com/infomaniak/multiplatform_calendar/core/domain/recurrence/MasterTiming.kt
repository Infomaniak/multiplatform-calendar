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
package com.infomaniak.multiplatform_calendar.core.domain.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The invariant timing model of a recurrence master, resolved once so the per-instance loop only has
 * to compute each varying start. [occurrenceEnd] preserves the master's nominal wall-clock duration (or its
 * whole-day span), matching RFC 5545 DST semantics (a 1-hour event stays 1 hour of wall-clock).
 */
internal class MasterTiming private constructor(
    val startZone: TimeZone,
    private val endZone: TimeZone,
    private val isAllDay: Boolean,
    private val nominalDuration: Duration,
    private val allDaySpanDays: Int,
) {
    /** The end wall-clock and instant of an occurrence starting at [startLocal] / [startInstant]. */
    fun occurrenceEnd(startLocal: LocalDateTime, startInstant: Instant): Pair<LocalDateTime, Instant> = if (isAllDay) {
        val endLocal = LocalDateTime(startLocal.date.plus(allDaySpanDays, DateTimeUnit.DAY), startLocal.time)
        endLocal to endLocal.toInstant(endZone)
    } else {
        val endInstant = startInstant + nominalDuration
        endInstant.toLocalDateTime(endZone) to endInstant
    }

    companion object {
        fun of(master: EventTiming, defaultZone: TimeZone) = MasterTiming(
            startZone = if (master.isAllDay) defaultZone else (master.startTimeZone ?: defaultZone),
            endZone = if (master.isAllDay) defaultZone else (master.endTimeZone ?: defaultZone),
            isAllDay = master.isAllDay,
            nominalDuration = master.endInstant(defaultZone) - master.startInstant(defaultZone),
            allDaySpanDays = if (master.isAllDay) master.start.date.daysUntil(master.end.date) else 0,
        )
    }
}
