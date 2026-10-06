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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventBounds
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ZonedWallClock
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

internal fun EventTimingEntity.toDomain(
    recurrenceRule: RecurrenceRule? = null,
    rDates: List<IcalDateValue> = emptyList(),
    exDates: List<IcalDateValue> = emptyList(),
): EventTiming = EventTiming(
    bounds = eventBounds(
        start = dtStart,
        // dtEndEffective already resolves DTEND/DURATION (and defaults to +1 day for AllDay).
        end = dtEndEffective,
        startZone = startTimeZone?.let(TimeZone::of),
        endZone = endTimeZone?.let(TimeZone::of),
        isAllDay = isAllDay,
    ),
    recurrenceRule = recurrenceRule,
    rDates = rDates,
    exDates = exDates,
)

/**
 * The [EventBounds] of stored wall-clocks and zones. Their form follows `DTSTART`: a floating start with a zoned
 * end, which RFC 5545 forbids, is read as floating.
 */
internal fun eventBounds(
    start: LocalDateTime,
    end: LocalDateTime,
    startZone: TimeZone?,
    endZone: TimeZone?,
    isAllDay: Boolean,
): EventBounds = when {
    isAllDay -> EventBounds.AllDay(start.date, end.date)
    startZone == null -> EventBounds.Floating(start, end)
    else -> EventBounds.Zoned(ZonedWallClock(start, startZone), ZonedWallClock(end, endZone ?: startZone))
}
