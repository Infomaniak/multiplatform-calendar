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
 * One bound (start or end) of an [EventTiming], in the original form it was described with.
 *
 * - [Floating]: a bare wall-clock with no zone (RFC 5545 FORM #1 and `DATE` values), read in whatever
 *   zone the reader is in.
 * - [Precise]: an absolute [Instant] (RFC 5545 FORM #2 UTC and FORM #3 `TZID`), with the [Precise.timeZone]
 *   it was originally described in, i.e. the zone to display it at in its original form.
 */
public sealed interface EventDateTime {
    /** The wall-clock in the event's original form: as-is when [Floating], at [Precise.timeZone] when [Precise]. */
    public val wallClock: LocalDateTime

    public data class Floating(override val wallClock: LocalDateTime) : EventDateTime

    /**
     * [wallClock] is the wall-clock the source described at [timeZone]. It only differs from [instant] read at
     * [timeZone] when the source described a wall-clock skipped by a DST gap (e.g. 02:30 on a spring-forward day
     * in Europe/Paris): it is kept as-is since a recurrence rule repeats the described wall-clock (RFC 5545 §3.3.10).
     */
    @ConsistentCopyVisibility
    public data class Precise internal constructor(
        val instant: Instant,
        val timeZone: TimeZone,
        override val wallClock: LocalDateTime,
    ) : EventDateTime {
        public constructor(instant: Instant, timeZone: TimeZone) : this(instant, timeZone, instant.toLocalDateTime(timeZone))
    }

    public companion object {
        /**
         * The [EventDateTime] a [wallClock] described at [timeZone] designates: [Floating] when [timeZone] is `null`,
         * [Precise] otherwise.
         *
         * A wall-clock repeated by a DST fall-back resolves to its earliest instant, and one skipped by a
         * spring-forward gap to the instant shifted forward by the gap's length (see [Precise.wallClock]).
         */
        public fun of(wallClock: LocalDateTime, timeZone: TimeZone?): EventDateTime = when (timeZone) {
            null -> Floating(wallClock)
            else -> Precise(wallClock.toInstant(timeZone), timeZone, wallClock)
        }
    }
}

/** Absolute point in time; a [EventDateTime.Floating] wall-clock is anchored in [defaultZone] (the reader's zone). */
internal fun EventDateTime.toInstant(defaultZone: TimeZone): Instant = when (this) {
    is EventDateTime.Floating -> wallClock.toInstant(defaultZone)
    is EventDateTime.Precise -> instant
}

/** Wall-clock at [targetZone]; a [EventDateTime.Floating] one is returned as-is, being read in the reader's zone. */
internal fun EventDateTime.toLocalDateTime(targetZone: TimeZone): LocalDateTime = when (this) {
    is EventDateTime.Floating -> wallClock
    is EventDateTime.Precise -> if (targetZone == timeZone) wallClock else instant.toLocalDateTime(targetZone)
}

/** The zone of a [EventDateTime.Precise], `null` for a [EventDateTime.Floating]. */
internal val EventDateTime.timeZoneOrNull: TimeZone? get() = (this as? EventDateTime.Precise)?.timeZone
