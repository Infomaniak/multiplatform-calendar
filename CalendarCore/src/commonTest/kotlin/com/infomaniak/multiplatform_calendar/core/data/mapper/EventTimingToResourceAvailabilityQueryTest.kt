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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.allDayTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.floatingTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.zonedTiming
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventTimingToResourceAvailabilityQueryTest {

    private val paris = TimeZone.of("Europe/Paris")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val newYork = TimeZone.of("America/New_York")

    @Test
    fun zonedTiming_keepsItsInstants_andUsesItsStartZone() {
        val start = LocalDateTime(2026, 6, 15, 9, 0)
        val end = LocalDateTime(2026, 6, 15, 21, 0)
        val query = zonedTiming(start, end, zone = newYork, endZone = paris).toResourceAvailabilityQuery(defaultZone = tokyo)

        assertEquals(
            ResourceAvailabilityQuery(start.toInstant(newYork), end.toInstant(paris), newYork, rrule = null),
            query,
        )
    }

    @Test
    fun floatingTiming_isAnchoredInDefaultZone() {
        val start = LocalDateTime(2026, 6, 15, 10, 0)
        val end = LocalDateTime(2026, 6, 15, 11, 0)
        val query = floatingTiming(start, end).toResourceAvailabilityQuery(defaultZone = tokyo)

        assertEquals(ResourceAvailabilityQuery(start.toInstant(tokyo), end.toInstant(tokyo), tokyo, rrule = null), query)
    }

    @Test
    fun allDayTiming_spansWholeDaysInDefaultZone() {
        val start = LocalDate(2026, 6, 15)
        val end = LocalDate(2026, 6, 16)
        val query = allDayTiming(start, end).toResourceAvailabilityQuery(defaultZone = paris)

        assertEquals(ResourceAvailabilityQuery(start.atStartOfDayIn(paris), end.atStartOfDayIn(paris), paris, rrule = null), query)
    }

    @Test
    fun recurrenceRule_isSerializedToRfc5545() {
        val rule = RecurrenceRule(freq = Frequency.Weekly, interval = 2, occurrenceCount = 5, weekStart = DayOfWeek.MONDAY)
        val timing = zonedTiming(LocalDateTime(2026, 6, 15, 9, 0), LocalDateTime(2026, 6, 15, 10, 0), paris, recurrenceRule = rule)

        assertEquals("FREQ=WEEKLY;COUNT=5;INTERVAL=2;WKST=MO", timing.toResourceAvailabilityQuery(defaultZone = paris).rrule)
    }

    @Test
    fun nonRecurringTiming_hasNoRrule() {
        val timing = floatingTiming(LocalDateTime(2026, 6, 15, 9, 0), LocalDateTime(2026, 6, 15, 10, 0))

        assertNull(timing.toResourceAvailabilityQuery(defaultZone = paris).rrule)
    }
}
