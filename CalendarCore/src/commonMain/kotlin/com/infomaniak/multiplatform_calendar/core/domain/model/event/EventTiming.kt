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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * When an event happens.
 *
 * RFC 5545 distinguishes two value types on `DTSTART`: `DATE` (whole-day, no time / timezone) and
 * `DATE-TIME` (anchored instant), and a `DATE-TIME` itself has three forms:
 * - FORM #1 "floating" — no `TZID`, no `Z`. The wall-clock is interpreted in the recipient's local time.
 * - FORM #2 UTC — `Z` suffix.
 * - FORM #3 "with timezone reference" — local wall-clock paired with an IANA `TZID`.
 *
 * The four cases are encoded here as follows (applied independently to start and end):
 * | Case                       | [start]/[end]                                            | [isAllDay] |
 * |----------------------------|----------------------------------------------------------|------------|
 * | `DATE` (whole-day)         | [EventDateTime.Floating] at midnight (time=0)            | `true`     |
 * | `DATE-TIME` UTC            | [EventDateTime.Precise] with `TimeZone.UTC`              | `false`    |
 * | `DATE-TIME` with `TZID`    | [EventDateTime.Precise] with the IANA zone               | `false`    |
 * | `DATE-TIME` floating       | [EventDateTime.Floating]                                 | `false`    |
 *
 * RFC 5545 §3.8.2.2 allows `DTEND` to carry a `TZID` different from `DTSTART` (e.g. a flight
 * "9:00 America/New_York → 16:00 Europe/Paris"), hence [start] and [end] are independent. For
 * all-day events both are [EventDateTime.Floating].
 *
 * When [isAllDay] is `true`, consumers should read [start] / [end] as dates only; the time
 * component is meaningless. [end] is exclusive (a single-day event has `end = start + 1d`),
 * matching iCal `DTEND;VALUE=DATE` semantics.
 *
 * Use [startInstant] / [endInstant] when you need an absolute point in time (display, comparisons).
 */
public data class EventTiming(
    val start: EventDateTime,
    val end: EventDateTime,
    val isAllDay: Boolean,
    val recurrenceRule: RecurrenceRule? = null,
    val rDates: List<IcalDateValue> = emptyList(),
    val exDates: List<IcalDateValue> = emptyList(),
) {
    init {
        require(!isAllDay || (start is EventDateTime.Floating && end is EventDateTime.Floating)) {
            "An all-day timing has no time zone, its bounds must be floating"
        }
    }

    /**
     * Resolve [EventTiming.start] to an absolute [Instant].
     *
     * - When [EventTiming.start] is [EventDateTime.Precise], its instant is returned.
     * - Otherwise (floating or all-day) it is anchored in [defaultZone] (recipient's local time per
     *   RFC 5545 FORM #1; the call-site supplies the device/user zone).
     */
    public fun startInstant(defaultZone: TimeZone): Instant = start.toInstant(defaultZone)

    /** See [startInstant]. Uses [EventTiming.end] (whose zone can differ from the start one). */
    public fun endInstant(defaultZone: TimeZone): Instant = end.toInstant(defaultZone)

    /**
     * Return [EventTiming.start] as a wall-clock in [targetZone].
     *
     * - Floating / all-day: returned as-is (per RFC 5545 FORM #1, a floating wall-clock is interpreted
     *   in the recipient's zone).
     * - Precise: its instant reprojected in [targetZone].
     */
    public fun startIn(targetZone: TimeZone): LocalDateTime = start.toLocalDateTime(targetZone)

    /** See [startIn]. Uses [EventTiming.end] (whose zone can differ from the start one). */
    public fun endIn(targetZone: TimeZone): LocalDateTime = end.toLocalDateTime(targetZone)

    /** Shortcut for [startInstant] with the device's current system zone. */
    public fun startInstantLocal(): Instant = startInstant(TimeZone.currentSystemDefault())

    /** Shortcut for [endInstant] with the device's current system zone. */
    public fun endInstantLocal(): Instant = endInstant(TimeZone.currentSystemDefault())

    /** Shortcut for [startIn] with the device's current system zone. */
    public fun startInLocal(): LocalDateTime = startIn(TimeZone.currentSystemDefault())

    /** Shortcut for [endIn] with the device's current system zone. */
    public fun endInLocal(): LocalDateTime = endIn(TimeZone.currentSystemDefault())
}

/** The wall-clock [EventTiming.start] was described with, see [EventDateTime.wallClock]. */
internal val EventTiming.startWallClock: LocalDateTime get() = start.wallClock

/** The wall-clock [EventTiming.end] was described with, see [EventDateTime.wallClock]. */
internal val EventTiming.endWallClock: LocalDateTime get() = end.wallClock

/** This timing moved onto the [start] / [end] wall-clocks, each bound keeping the zone it is described in. */
internal fun EventTiming.withWallClocks(
    start: LocalDateTime = startWallClock,
    end: LocalDateTime = endWallClock,
): EventTiming = copy(start = EventDateTime.of(start, startTimeZone), end = EventDateTime.of(end, endTimeZone))

/** The zone [EventTiming.start] was described in, `null` when floating or all-day. */
internal val EventTiming.startTimeZone: TimeZone? get() = start.timeZoneOrNull

/** The zone [EventTiming.end] was described in, `null` when floating or all-day. */
internal val EventTiming.endTimeZone: TimeZone? get() = end.timeZoneOrNull
