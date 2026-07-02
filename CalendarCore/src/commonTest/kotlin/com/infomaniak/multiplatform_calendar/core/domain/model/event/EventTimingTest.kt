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

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EventTimingTest {

    private val paris = TimeZone.of("Europe/Paris")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val newYork = TimeZone.of("America/New_York")

    // ---- startInstant / endInstant --------------------------------------------------------------

    @Test
    fun startInstant_zonedEvent_usesOwnZone() {
        val timing = zoned(start = ldt(2026, 6, 15, 14, 0), zone = paris)
        val expected = ldt(2026, 6, 15, 14, 0).toInstant(paris)
        // defaultZone should be ignored when the event carries its own zone.
        assertEquals(expected, timing.startInstant(defaultZone = tokyo))
    }

    @Test
    fun startInstant_floating_usesDefaultZone() {
        // Floating "10:00" anchored in Tokyo per RFC 5545 FORM #1.
        val timing = floating(start = ldt(2026, 6, 15, 10, 0))
        val expected = ldt(2026, 6, 15, 10, 0).toInstant(tokyo)
        assertEquals(expected, timing.startInstant(defaultZone = tokyo))
    }

    @Test
    fun endInstant_usesEndZone_notStartZone_forCrossZoneEvent() {
        val timing = EventTiming(
            start = ldt(2026, 6, 15, 9, 0),
            end = ldt(2026, 6, 15, 21, 0),
            startTimeZone = newYork,
            endTimeZone = paris,
            isAllDay = false,
        )
        assertEquals(ldt(2026, 6, 15, 9, 0).toInstant(newYork), timing.startInstant(TimeZone.UTC))
        assertEquals(ldt(2026, 6, 15, 21, 0).toInstant(paris), timing.endInstant(TimeZone.UTC))
    }

    // ---- startIn / endIn (LocalDateTime reprojection) -------------------------------------------

    @Test
    fun startIn_sameZone_returnsStartAsIs_withoutRoundTrip() {
        val start = ldt(2026, 6, 15, 14, 0)
        val timing = zoned(start = start, zone = paris)
        // Fast path: identity, no conversion.
        assertSame(start, timing.startIn(paris))
    }

    @Test
    fun startIn_floating_returnsStartAsIs_forAnyTargetZone() {
        val start = ldt(2026, 6, 15, 10, 0)
        val timing = floating(start = start)
        assertSame(start, timing.startIn(paris))
        assertSame(start, timing.startIn(tokyo))
        assertSame(start, timing.startIn(TimeZone.UTC))
    }

    @Test
    fun startIn_differentZone_reprojectsViaInstant() {
        // Paris 14:00 (summer, UTC+2) → Tokyo 21:00 (UTC+9), delta +7h.
        val timing = zoned(start = ldt(2026, 6, 15, 14, 0), zone = paris)
        assertEquals(ldt(2026, 6, 15, 21, 0), timing.startIn(tokyo))
    }

    @Test
    fun endIn_usesEndZone_notStartZone() {
        val timing = EventTiming(
            start = ldt(2026, 6, 15, 9, 0),
            end = ldt(2026, 6, 15, 21, 0),
            startTimeZone = newYork,
            endTimeZone = paris,
            isAllDay = false,
        )
        // Paris 21:00 == 19:00 UTC == Tokyo 04:00 next day.
        assertEquals(ldt(2026, 6, 16, 4, 0), timing.endIn(tokyo))
    }

    // ---- Local variants (device zone) -----------------------------------------------------------

    @Test
    fun startInLocal_matchesStartIn_currentSystemDefault() {
        val timing = zoned(start = ldt(2026, 6, 15, 14, 0), zone = paris)
        val current = TimeZone.currentSystemDefault()
        assertEquals(timing.startIn(current), timing.startInLocal())
    }

    @Test
    fun startInstantLocal_matchesStartInstant_currentSystemDefault() {
        val timing = floating(start = ldt(2026, 6, 15, 10, 0))
        val current = TimeZone.currentSystemDefault()
        assertEquals(timing.startInstant(current), timing.startInstantLocal())
    }

    // ---- All-day --------------------------------------------------------------------------------

    @Test
    fun startIn_allDay_returnsMidnightAsIs_regardlessOfTargetZone() {
        // All-day events store both zones as null; startIn should not attempt any reprojection.
        val start = ldt(2026, 6, 15, 0, 0)
        val timing = EventTiming(
            start = start,
            end = ldt(2026, 6, 16, 0, 0),
            startTimeZone = null,
            endTimeZone = null,
            isAllDay = true,
        )
        assertSame(start, timing.startIn(paris))
        assertSame(start, timing.startIn(tokyo))
    }

    // ---- UTC ------------------------------------------------------------------------------------

    @Test
    fun startIn_utcEvent_reprojectsToTargetZone() {
        val timing = zoned(start = ldt(2026, 6, 15, 12, 0), zone = TimeZone.UTC)
        assertEquals(ldt(2026, 6, 15, 14, 0), timing.startIn(paris))
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun ldt(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime(year, month, day, hour, minute)

    private fun zoned(start: LocalDateTime, zone: TimeZone): EventTiming = EventTiming(
        start = start,
        end = LocalDateTime(start.date, start.time),
        startTimeZone = zone,
        endTimeZone = zone,
        isAllDay = false,
    )

    private fun floating(start: LocalDateTime): EventTiming = EventTiming(
        start = start,
        end = LocalDateTime(start.date, start.time),
        startTimeZone = null,
        endTimeZone = null,
        isAllDay = false,
    )
}
