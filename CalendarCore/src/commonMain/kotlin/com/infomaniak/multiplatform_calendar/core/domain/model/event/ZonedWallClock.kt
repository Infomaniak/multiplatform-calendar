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

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A wall-clock described in a [timeZone] (RFC 5545 FORM #2 when [timeZone] is UTC, FORM #3 otherwise).
 *
 * The wall-clock is the source of truth, as in iCal: a wall-clock skipped by a DST gap is kept as described,
 * while [instant] resolves it forward by the gap's length.
 */
public data class ZonedWallClock(val wallClock: LocalDateTime, val timeZone: TimeZone) {

    public val instant: Instant get() = wallClock.toInstant(timeZone)

    /** The wall-clock read in [targetZone]; the described one as-is when [targetZone] is its own zone. */
    public fun wallClockIn(targetZone: TimeZone): LocalDateTime =
        if (targetZone == timeZone) wallClock else instant.toLocalDateTime(targetZone)
}
