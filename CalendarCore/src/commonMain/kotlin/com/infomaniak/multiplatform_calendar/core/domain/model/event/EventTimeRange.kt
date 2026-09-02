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
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * The half-open time range and recurrence definition of an event.
 *
 * [start] and [end] independently preserve RFC 5545 floating or precise semantics, including
 * events whose `DTSTART` and `DTEND` use different time zones.
 */
public data class EventTimeRange(
    val start: EventTiming,
    val end: EventTiming,
    val isAllDay: Boolean,
    val recurrenceRule: RecurrenceRule? = null,
    val rDates: List<IcalDateValue> = emptyList(),
    val exDates: List<IcalDateValue> = emptyList(),
) {
    internal val startLocalDateTime: LocalDateTime get() = start.localDateTime()
    internal val endLocalDateTime: LocalDateTime get() = end.localDateTime()
    internal val startTimeZone: TimeZone? get() = (start as? EventTiming.Precised)?.timeZone
    internal val endTimeZone: TimeZone? get() = (end as? EventTiming.Precised)?.timeZone

    /** Resolve [start] to an absolute instant, anchoring a floating value in [defaultZone]. */
    public fun startInstant(defaultZone: TimeZone): Instant = start.instant(defaultZone)

    /** Resolve [end] to an absolute instant, anchoring a floating value in [defaultZone]. */
    public fun endInstant(defaultZone: TimeZone): Instant = end.instant(defaultZone)

    /** Reproject [start] into [targetZone], or return a floating wall-clock unchanged. */
    public fun startIn(targetZone: TimeZone): LocalDateTime = start.inTimeZone(targetZone)

    /** Reproject [end] into [targetZone], or return a floating wall-clock unchanged. */
    public fun endIn(targetZone: TimeZone): LocalDateTime = end.inTimeZone(targetZone)

    public fun startInstantLocal(): Instant = startInstant(TimeZone.currentSystemDefault())
    public fun endInstantLocal(): Instant = endInstant(TimeZone.currentSystemDefault())
    public fun startInLocal(): LocalDateTime = startIn(TimeZone.currentSystemDefault())
    public fun endInLocal(): LocalDateTime = endIn(TimeZone.currentSystemDefault())
}

internal fun eventTimingOf(localDateTime: LocalDateTime, timeZone: TimeZone?): EventTiming =
    if (timeZone == null) {
        EventTiming.Floating(localDateTime)
    } else {
        EventTiming.Precised(localDateTime.toInstant(timeZone), timeZone)
    }
