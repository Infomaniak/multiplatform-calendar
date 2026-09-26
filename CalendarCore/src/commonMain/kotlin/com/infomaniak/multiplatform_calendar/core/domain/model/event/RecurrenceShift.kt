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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.extensions.shiftedBy
import com.infomaniak.multiplatform_calendar.core.extensions.wallClockShift
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

/** This timing moved whole by [delta], both ends together, so it keeps the length it had. */
internal fun EventTiming.shiftedBy(delta: Duration): EventTiming =
    withWallClocks(start = startWallClock.shiftedBy(delta), end = endWallClock.shiftedBy(delta))

/**
 * This value moved by [delta], so it goes on designating the occurrence it designated before the
 * series it belongs to was moved.
 *
 * A zoned value is moved on its own local clock and re-anchored afterwards: a series pushed by an hour
 * stays at the hour it is read at, whichever side of a DST transition each of its dates falls.
 */
internal fun IcalDateValue.shiftedBy(delta: Duration): IcalDateValue = when (this) {
    is IcalDateValue.AllDay -> IcalDateValue.AllDay(date.atTime(0, 0).shiftedBy(delta).date)
    is IcalDateValue.Floating -> IcalDateValue.Floating(localDateTime.shiftedBy(delta))
    is IcalDateValue.Zoned -> TimeZone.of(timeZoneId).let { zone ->
        IcalDateValue.Zoned(instant.toLocalDateTime(zone).shiftedBy(delta).toInstant(zone), timeZoneId)
    }
}

/** See [IcalDateValue.shiftedBy]. A UTC-anchored master reads its calendar face in [TimeZone.UTC] already. */
internal fun RecurrenceKey.shiftedBy(delta: Duration): RecurrenceKey = when (this) {
    is RecurrenceKey.AllDay -> RecurrenceKey.AllDay(date.atTime(0, 0).shiftedBy(delta).date)
    is RecurrenceKey.Floating -> RecurrenceKey.Floating(localDateTime.shiftedBy(delta))
    is RecurrenceKey.Zoned -> RecurrenceKey.Zoned(localDateTime.shiftedBy(delta), timeZoneId)
    is RecurrenceKey.Utc -> RecurrenceKey.Utc(instant + delta)
}

/**
 * This rule with the end date it may carry moved by [delta], so the series it bounds keeps the last
 * occurrence it had: left where it was, an end date cuts one the move pushed past it.
 */
internal fun RecurrenceRule.shiftedBy(delta: Duration): RecurrenceRule =
    until?.let { copy(until = it.shiftedBy(delta)) } ?: this

/** See [IcalDateValue.shiftedBy]. */
private fun RecurrenceUntil.shiftedBy(delta: Duration): RecurrenceUntil = when (this) {
    is RecurrenceUntil.DateOnly -> RecurrenceUntil.DateOnly(date.atTime(0, 0).shiftedBy(delta).date)
    is RecurrenceUntil.DateTimeUtc -> RecurrenceUntil.DateTimeUtc(instant + delta)
    is RecurrenceUntil.Floating -> RecurrenceUntil.Floating(dateTime.shiftedBy(delta))
}
