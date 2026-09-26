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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant

class EventDateTimeTest {

    private val paris = TimeZone.of("Europe/Paris")

    @Test
    fun of_withoutZone_isFloating() {
        val wallClock = LocalDateTime(2026, 6, 15, 10, 0)
        assertEquals(EventDateTime.Floating(wallClock), EventDateTime.of(wallClock, timeZone = null))
    }

    @Test
    fun of_withZone_isPreciseAtThatInstant() {
        val wallClock = LocalDateTime(2026, 6, 15, 14, 0)
        val precise = assertIs<EventDateTime.Precise>(EventDateTime.of(wallClock, paris))
        assertEquals(Instant.parse("2026-06-15T12:00:00Z"), precise.instant)
        assertEquals(paris, precise.timeZone)
        assertEquals(wallClock, precise.wallClock)
        assertEquals(EventDateTime.Precise(precise.instant, paris), precise)
    }

    @Test
    fun of_wallClockInDstGap_keepsDescribedWallClock_andResolvesInstantForward() {
        // 02:30 does not exist on 2026-03-29 in Europe/Paris (02:00 → 03:00).
        val wallClock = LocalDateTime(2026, 3, 29, 2, 30)
        val precise = assertIs<EventDateTime.Precise>(EventDateTime.of(wallClock, paris))
        assertEquals(wallClock, precise.wallClock)
        assertEquals(wallClock.toInstant(paris), precise.instant)
        // The same instant built without a described wall-clock reads back the actual one.
        assertEquals(LocalDateTime(2026, 3, 29, 3, 30), EventDateTime.Precise(precise.instant, paris).wallClock)
    }

    @Test
    fun allDayTiming_rejectsPreciseBounds() {
        assertFailsWith<IllegalArgumentException> {
            EventTiming(
                start = EventDateTime.of(LocalDateTime(2026, 6, 15, 0, 0), paris),
                end = EventDateTime.of(LocalDateTime(2026, 6, 16, 0, 0), paris),
                isAllDay = true,
            )
        }
    }
}
