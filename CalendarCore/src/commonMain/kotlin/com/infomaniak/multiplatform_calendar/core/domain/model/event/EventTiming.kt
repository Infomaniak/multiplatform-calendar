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
 * When an event happens: its [bounds], and the recurrence set they repeat on.
 *
 * RFC 5545 values map onto [EventBounds] as follows:
 * | `DTSTART` / `DTEND`        | [bounds]                                       |
 * |----------------------------|------------------------------------------------|
 * | `DATE` (whole-day)         | [EventBounds.AllDay]                           |
 * | `DATE-TIME` floating       | [EventBounds.Floating]                         |
 * | `DATE-TIME` UTC            | [EventBounds.Zoned] in `TimeZone.UTC`          |
 * | `DATE-TIME` with `TZID`    | [EventBounds.Zoned] in that zone               |
 *
 * Use [startInstant] / [endInstant] when you need an absolute point in time (display, comparisons).
 */
public data class EventTiming(
    val bounds: EventBounds,
    val recurrenceRule: RecurrenceRule? = null,
    val rDates: List<IcalDateValue> = emptyList(),
    val exDates: List<IcalDateValue> = emptyList(),
) {
    val isAllDay: Boolean get() = bounds is EventBounds.AllDay

    /** See [EventBounds.startInstant]. */
    public fun startInstant(defaultZone: TimeZone): Instant = bounds.startInstant(defaultZone)

    /** See [EventBounds.endInstant]. */
    public fun endInstant(defaultZone: TimeZone): Instant = bounds.endInstant(defaultZone)

    /** See [EventBounds.startIn]. */
    public fun startIn(targetZone: TimeZone): LocalDateTime = bounds.startIn(targetZone)

    /** See [EventBounds.endIn]. */
    public fun endIn(targetZone: TimeZone): LocalDateTime = bounds.endIn(targetZone)

    /** Shortcut for [startInstant] with the device's current system zone. */
    public fun startInstantLocal(): Instant = startInstant(TimeZone.currentSystemDefault())

    /** Shortcut for [endInstant] with the device's current system zone. */
    public fun endInstantLocal(): Instant = endInstant(TimeZone.currentSystemDefault())

    /** Shortcut for [startIn] with the device's current system zone. */
    public fun startInLocal(): LocalDateTime = startIn(TimeZone.currentSystemDefault())

    /** Shortcut for [endIn] with the device's current system zone. */
    public fun endInLocal(): LocalDateTime = endIn(TimeZone.currentSystemDefault())
}
