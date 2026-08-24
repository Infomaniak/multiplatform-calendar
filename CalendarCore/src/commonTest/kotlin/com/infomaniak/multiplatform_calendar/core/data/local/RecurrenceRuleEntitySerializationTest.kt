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
package com.infomaniak.multiplatform_calendar.core.data.local

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.WeekDayNum
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * `RecurrenceRule` is persisted as a JSON blob through [CalendarTypeConverters]. This exercises the
 * real Room converter path so a broken `RecurrenceRule`/`RecurrenceUntil` serializer surfaces here
 * instead of at DB write time.
 */
class RecurrenceRuleEntitySerializationTest {

    private val converters = CalendarTypeConverters()

    private fun roundTrip(rule: RecurrenceRule): RecurrenceRule? {
        val stored = converters.fromRecurrenceRule(rule)
        return converters.toRecurrenceRule(stored)
    }

    @Test
    fun richRule_roundTrips_throughConverter() {
        val original = RecurrenceRule(
            freq = Frequency.Weekly,
            interval = 2,
            byDay = listOf(WeekDayNum(ordinal = 1, dayOfWeek = DayOfWeek.MONDAY)),
            until = RecurrenceUntil.DateOnly(LocalDate(2026, 7, 1)),
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun nullRule_roundTrips_asNull() {
        assertNull(converters.fromRecurrenceRule(null))
        assertNull(converters.toRecurrenceRule(null))
    }

    @Test
    fun icalDateValues_roundTrip_throughConverter() {
        val original = listOf(
            IcalDateValue.AllDay(LocalDate(2026, 7, 1)),
            IcalDateValue.Floating(LocalDateTime(2026, 7, 2, 10, 30)),
            IcalDateValue.Zoned(Instant.parse("2026-07-03T09:00:00Z"), "Europe/Paris"),
        )

        val stored = converters.fromIcalDateValues(original)
        val restored = converters.toIcalDateValues(stored)
        assertEquals(original, restored)
    }
}
