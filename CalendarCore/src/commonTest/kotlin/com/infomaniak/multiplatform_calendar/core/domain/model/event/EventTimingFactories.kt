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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

internal fun allDayTiming(
    start: LocalDate,
    end: LocalDate,
    recurrenceRule: RecurrenceRule? = null,
    rDates: List<IcalDateValue> = emptyList(),
    exDates: List<IcalDateValue> = emptyList(),
) = EventTiming(EventBounds.AllDay(start, end), recurrenceRule, rDates, exDates)

internal fun floatingTiming(
    start: LocalDateTime,
    end: LocalDateTime,
    recurrenceRule: RecurrenceRule? = null,
    rDates: List<IcalDateValue> = emptyList(),
    exDates: List<IcalDateValue> = emptyList(),
) = EventTiming(EventBounds.Floating(start, end), recurrenceRule, rDates, exDates)

internal fun zonedTiming(
    start: LocalDateTime,
    end: LocalDateTime,
    zone: TimeZone,
    endZone: TimeZone = zone,
    recurrenceRule: RecurrenceRule? = null,
    rDates: List<IcalDateValue> = emptyList(),
    exDates: List<IcalDateValue> = emptyList(),
) = EventTiming(zonedBounds(start, end, zone, endZone), recurrenceRule, rDates, exDates)

internal fun zonedBounds(start: LocalDateTime, end: LocalDateTime, zone: TimeZone, endZone: TimeZone = zone) =
    EventBounds.Zoned(ZonedWallClock(start, zone), ZonedWallClock(end, endZone))
