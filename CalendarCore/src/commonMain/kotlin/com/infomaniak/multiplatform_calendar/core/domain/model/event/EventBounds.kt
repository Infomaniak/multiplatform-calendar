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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * The start and end of an event, in one of the forms RFC 5545 allows for `DTSTART` / `DTEND`. Both bounds share
 * the form of `DTSTART`; only the zones of a [Zoned] event may differ (e.g. a flight New York → Paris).
 */
public sealed interface EventBounds {

    /** A form without zone, read in the reader's zone (RFC 5545 FORM #1 and `DATE` values). */
    public sealed interface Unanchored : EventBounds

    /** `DATE` values: whole days, [end] exclusive (a single-day event ends the next day). */
    public data class AllDay(val start: LocalDate, val end: LocalDate) : Unanchored

    /** Floating `DATE-TIME` values (RFC 5545 FORM #1). */
    public data class Floating(val start: LocalDateTime, val end: LocalDateTime) : Unanchored

    /** UTC or `TZID` `DATE-TIME` values (RFC 5545 FORM #2 / #3). */
    public data class Zoned(val start: ZonedWallClock, val end: ZonedWallClock) : EventBounds

    /** The start as an absolute point in time; an [Unanchored] one is anchored in [defaultZone], the reader's zone. */
    public fun startInstant(defaultZone: TimeZone): Instant = when (this) {
        is Unanchored -> startWallClock.toInstant(defaultZone)
        is Zoned -> start.instant
    }

    /** See [startInstant]. */
    public fun endInstant(defaultZone: TimeZone): Instant = when (this) {
        is Unanchored -> endWallClock.toInstant(defaultZone)
        is Zoned -> end.instant
    }

    /** The start as a wall-clock in [targetZone]; an [Unanchored] one is returned as-is, being read in any zone. */
    public fun startIn(targetZone: TimeZone): LocalDateTime = when (this) {
        is Unanchored -> startWallClock
        is Zoned -> start.wallClockIn(targetZone)
    }

    /** See [startIn]. */
    public fun endIn(targetZone: TimeZone): LocalDateTime = when (this) {
        is Unanchored -> endWallClock
        is Zoned -> end.wallClockIn(targetZone)
    }
}

/** The wall-clock `DTSTART` is described with; midnight for an [EventBounds.AllDay] one. */
internal val EventBounds.startWallClock: LocalDateTime
    get() = when (this) {
        is EventBounds.AllDay -> start.atTime(LocalTime(0, 0))
        is EventBounds.Floating -> start
        is EventBounds.Zoned -> start.wallClock
    }

/** See [startWallClock]. */
internal val EventBounds.endWallClock: LocalDateTime
    get() = when (this) {
        is EventBounds.AllDay -> end.atTime(LocalTime(0, 0))
        is EventBounds.Floating -> end
        is EventBounds.Zoned -> end.wallClock
    }
