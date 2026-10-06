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

import com.infomaniak.multiplatform_calendar.core.data.mapper.eventBounds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EventBoundsTest {

    @Test
    fun zonedWallClock_inDstGap_keepsItsWallClockAndResolvesForward() {
        val skipped = ZonedWallClock(LocalDateTime(2026, 3, 29, 2, 30), PARIS)

        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), skipped.instant)
        assertEquals(LocalDateTime(2026, 3, 29, 2, 30), skipped.wallClockIn(PARIS))
        assertEquals(LocalDateTime(2026, 3, 29, 1, 30), skipped.wallClockIn(TimeZone.UTC))
    }

    @Test
    fun zonedWallClock_inDstOverlap_keepsItsWallClockAndResolvesToTheEarlierOffset() {
        val repeated = ZonedWallClock(LocalDateTime(2026, 10, 25, 2, 30), PARIS)

        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), repeated.instant)
        assertEquals(LocalDateTime(2026, 10, 25, 2, 30), repeated.wallClockIn(PARIS))
    }

    @Test
    fun zoned_acrossTwoZones_readsEachBoundInItsOwnZone() {
        val flight = EventBounds.Zoned(
            start = ZonedWallClock(LocalDateTime(2026, 6, 15, 18, 0), NEW_YORK),
            end = ZonedWallClock(LocalDateTime(2026, 6, 16, 8, 0), PARIS),
        )

        assertEquals(Instant.parse("2026-06-15T22:00:00Z"), flight.startInstant(defaultZone = TimeZone.UTC))
        assertEquals(Instant.parse("2026-06-16T06:00:00Z"), flight.endInstant(defaultZone = TimeZone.UTC))
        assertEquals(LocalDateTime(2026, 6, 16, 0, 0), flight.startIn(PARIS))
        assertEquals(LocalDateTime(2026, 6, 16, 2, 0), flight.endIn(NEW_YORK))
    }

    @Test
    fun floating_isReadInTheReaderZone() {
        val bounds = EventBounds.Floating(START, END)

        assertEquals(START, bounds.startIn(NEW_YORK))
        assertEquals(END, bounds.endIn(PARIS))
        assertEquals(START.toInstant(PARIS), bounds.startInstant(defaultZone = PARIS))
    }

    @Test
    fun allDay_spansWholeDaysOfTheReaderZone() {
        val bounds = EventBounds.AllDay(LocalDate(2026, 6, 15), LocalDate(2026, 6, 17))

        assertEquals(LocalDateTime(2026, 6, 15, 0, 0), bounds.startIn(NEW_YORK))
        assertEquals(LocalDateTime(2026, 6, 17, 0, 0), bounds.endIn(NEW_YORK))
        assertEquals(Instant.parse("2026-06-16T22:00:00Z"), bounds.endInstant(defaultZone = PARIS))
    }

    @Test
    fun eventBounds_withAFloatingStartAndAZonedEnd_isFloating() {
        val bounds = eventBounds(START, END, startZone = null, endZone = PARIS, isAllDay = false)

        assertEquals(EventBounds.Floating(START, END), bounds)
    }

    @Test
    fun eventBounds_withoutEndZone_endsInTheStartZone() {
        val bounds = eventBounds(START, END, startZone = PARIS, endZone = null, isAllDay = false)

        assertEquals(EventBounds.Zoned(ZonedWallClock(START, PARIS), ZonedWallClock(END, PARIS)), bounds)
    }

    @Test
    fun eventBounds_allDay_ignoresZones() {
        val bounds = eventBounds(START, END, startZone = PARIS, endZone = PARIS, isAllDay = true)

        assertEquals(EventBounds.AllDay(START.date, END.date), bounds)
    }

    private companion object {
        val PARIS = TimeZone.of("Europe/Paris")
        val NEW_YORK = TimeZone.of("America/New_York")
        val START = LocalDateTime(2026, 6, 15, 10, 0)
        val END = LocalDateTime(2026, 6, 15, 11, 0)
    }
}
