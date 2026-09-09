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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The emitted `RECURRENCE-ID` must take the value type of the master's `DTSTART` (RFC 5545 §3.8.4.4),
 * whatever form the key itself carries — otherwise the server detaches the override into an orphan
 * instance instead of pairing it with the occurrence it replaces.
 */
class EventEditMapperRecurrenceIdTest {

    @Test
    fun zonedMasterEmitsAFloatingValueAlongsideItsTzid() {
        val paris = TimeZone.of("Europe/Paris")
        val key = RecurrenceKey.Zoned(LocalDateTime(2026, 6, 15, 10, 0), paris.id)

        val recurrenceId = assertNotNull(key.toRemoteRecurrenceId(timing(zone = paris)))

        assertEquals("Europe/Paris", recurrenceId.tzid)
        assertEquals(false, recurrenceId.isDateOnly)
        // FORM #3: the wall-clock alone, the zone living in the TZID parameter.
        assertEquals("20260615T100000", recurrenceId.value)
    }

    @Test
    fun utcMasterEmitsAZSuffixedValueWithoutTzid() {
        val key = RecurrenceKey.Utc(LocalDateTime(2026, 6, 15, 10, 0).toInstant(TimeZone.UTC))

        val recurrenceId = assertNotNull(key.toRemoteRecurrenceId(timing(zone = TimeZone.UTC)))

        assertNull(recurrenceId.tzid, "UTC is carried by the Z suffix, never by a TZID")
        assertEquals(false, recurrenceId.isDateOnly)
        assertEquals("20260615T100000Z", recurrenceId.value)
    }

    @Test
    fun floatingMasterEmitsABareWallClock() {
        val key = RecurrenceKey.Floating(LocalDateTime(2026, 6, 15, 10, 0))

        val recurrenceId = assertNotNull(key.toRemoteRecurrenceId(timing(zone = null)))

        assertNull(recurrenceId.tzid)
        assertEquals(false, recurrenceId.isDateOnly)
        assertEquals("20260615T100000", recurrenceId.value)
    }

    @Test
    fun allDayMasterEmitsADateOnlyValue() {
        val key = RecurrenceKey.AllDay(LocalDate(2026, 6, 15))

        val recurrenceId = assertNotNull(key.toRemoteRecurrenceId(timing(zone = null, isAllDay = true)))

        assertNull(recurrenceId.tzid, "VALUE=DATE takes precedence over any zone")
        assertEquals(true, recurrenceId.isDateOnly)
        assertEquals("20260615", recurrenceId.value)
    }

    @Test
    fun aDateKeyAgainstATimedMasterKeepsTheMastersTimeOfDay() {
        val paris = TimeZone.of("Europe/Paris")
        val key = RecurrenceKey.AllDay(LocalDate(2026, 6, 15))

        val recurrenceId = assertNotNull(key.toRemoteRecurrenceId(timing(zone = paris)))

        // Reading the key's own value would land on midnight and designate no occurrence at all.
        assertEquals("20260615T100000", recurrenceId.value)
        assertEquals("Europe/Paris", recurrenceId.tzid)
    }

    @Test
    fun aZonedKeyAgainstAFloatingMasterDesignatesNothing() {
        val key = RecurrenceKey.Zoned(LocalDateTime(2026, 6, 15, 10, 0), "Europe/Paris")

        assertNull(key.toRemoteRecurrenceId(timing(zone = null)))
    }

    private fun timing(zone: TimeZone?, isAllDay: Boolean = false) = EventTiming(
        start = LocalDateTime(2026, 6, 15, 10, 0),
        end = LocalDateTime(2026, 6, 15, 11, 0),
        startTimeZone = zone,
        endTimeZone = zone,
        isAllDay = isAllDay,
    )
}
