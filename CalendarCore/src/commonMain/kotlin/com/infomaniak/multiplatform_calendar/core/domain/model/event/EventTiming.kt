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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
 * | Case                       | [start]/[end]                  | [startTimeZone]/[endTimeZone] | [isAllDay] |
 * |----------------------------|--------------------------------|-------------------------------|------------|
 * | `DATE` (whole-day)         | wall-clock midnight (time=0)   | `null`                        | `true`     |
 * | `DATE-TIME` UTC            | wall-clock in UTC              | `TimeZone.UTC`                | `false`    |
 * | `DATE-TIME` with `TZID`    | wall-clock in that zone        | the IANA zone                 | `false`    |
 * | `DATE-TIME` floating       | wall-clock                     | `null`                        | `false`    *
 *
 * RFC 5545 §3.8.2.2 allows `DTEND` to carry a `TZID` different from `DTSTART` (e.g. a flight
 * "9:00 America/New_York → 16:00 Europe/Paris"), hence the two zones are kept independent. For
 * all-day events both zones are `null`.
 *
 * When [isAllDay] is `true`, consumers should read [start] / [end] as dates only; the time
 * component is meaningless. [end] is exclusive (a single-day event has `end = start + 1d`),
 * matching iCal `DTEND;VALUE=DATE` semantics.
 *
 * Use [startInstant] / [endInstant] when you need an absolute point in time (display, comparisons).
 */
public data class EventTiming(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val startTimeZone: TimeZone?,
    val endTimeZone: TimeZone?,
    val isAllDay: Boolean,
    val recurrenceRule: RecurrenceRule? = null,
) {
    /**
     * Resolve [EventTiming.start] to an absolute [Instant].
     *
     * - When [EventTiming.startTimeZone] is set, the wall-clock is anchored in that zone.
     * - Otherwise (floating or all-day) it is anchored in [defaultZone] (recipient's local time per
     *   RFC 5545 FORM #1; the call-site supplies the device/user zone).
     */
    public fun startInstant(defaultZone: TimeZone): Instant =
        start.toInstant(startTimeZone ?: defaultZone)

    /** See [startInstant]. Uses [EventTiming.endTimeZone] (which can differ from the start zone). */
    public fun endInstant(defaultZone: TimeZone): Instant =
        end.toInstant(endTimeZone ?: defaultZone)

    /**
     * Return [EventTiming.start] as a wall-clock in [targetZone].
     *
     * - Floating / all-day ([EventTiming.startTimeZone] `== null`): returned as-is (per RFC 5545
     *   FORM #1, a floating wall-clock is interpreted in the recipient's zone).
     * - Same zone as [targetZone]: returned as-is (no-op conversion).
     * - Different zone: reprojected via an absolute [Instant].
     */
    public fun startIn(targetZone: TimeZone): LocalDateTime = when (startTimeZone) {
        null, targetZone -> start
        else -> start.toInstant(startTimeZone).toLocalDateTime(targetZone)
    }

    /** See [startIn]. Uses [EventTiming.endTimeZone] (which can differ from the start zone). */
    public fun endIn(targetZone: TimeZone): LocalDateTime = when (endTimeZone) {
        null, targetZone -> end
        else -> end.toInstant(endTimeZone).toLocalDateTime(targetZone)
    }

    /** Shortcut for [startInstant] with the device's current system zone. */
    public fun startInstantLocal(): Instant = startInstant(TimeZone.currentSystemDefault())

    /** Shortcut for [endInstant] with the device's current system zone. */
    public fun endInstantLocal(): Instant = endInstant(TimeZone.currentSystemDefault())

    /** Shortcut for [startIn] with the device's current system zone. */
    public fun startInLocal(): LocalDateTime = startIn(TimeZone.currentSystemDefault())

    /** Shortcut for [endIn] with the device's current system zone. */
    public fun endInLocal(): LocalDateTime = endIn(TimeZone.currentSystemDefault())
}
