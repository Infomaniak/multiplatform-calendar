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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

/**
 * How far [to] lies from [from] on the calendar face.
 *
 * Measured in [UTC], a zone with no transition to trip over, so the distance is the one a reader sees
 * on the clock rather than the one the offsets in between would make of it.
 */
internal fun wallClockShift(from: LocalDateTime, to: LocalDateTime): Duration =
    to.toInstant(UTC) - from.toInstant(UTC)

/** This calendar face moved by [delta] (see [wallClockShift]). */
internal fun LocalDateTime.shiftedBy(delta: Duration): LocalDateTime = (toInstant(UTC) + delta).toLocalDateTime(UTC)

/** This timing moved whole by [delta], both ends together, so it keeps the length it had. */
internal fun EventTiming.shiftedBy(delta: Duration): EventTiming =
    copy(start = start.shiftedBy(delta), end = end.shiftedBy(delta))

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

/** See [IcalDateValue.shiftedBy]. A UTC-anchored master reads its calendar face in [UTC] already. */
internal fun RecurrenceKey.shiftedBy(delta: Duration): RecurrenceKey = when (this) {
    is RecurrenceKey.AllDay -> RecurrenceKey.AllDay(date.atTime(0, 0).shiftedBy(delta).date)
    is RecurrenceKey.Floating -> RecurrenceKey.Floating(localDateTime.shiftedBy(delta))
    is RecurrenceKey.Zoned -> RecurrenceKey.Zoned(localDateTime.shiftedBy(delta), timeZoneId)
    is RecurrenceKey.Utc -> RecurrenceKey.Utc(instant + delta)
}
