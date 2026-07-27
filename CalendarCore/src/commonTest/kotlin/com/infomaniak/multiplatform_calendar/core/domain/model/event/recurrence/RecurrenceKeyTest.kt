/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.AllDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Floating
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Utc
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Zoned
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RecurrenceKeyTest {

    @Test
    fun allDayCanonicalEncodesDate() {
        val key = AllDay(LocalDate(2026, 3, 14))
        assertEquals("D:2026-03-14", key.canonical)
    }

    @Test
    fun floatingCanonicalEncodesLocalDateTime() {
        val key = Floating(LocalDateTime(2026, 3, 14, 9, 30))
        assertEquals("F:2026-03-14T09:30", key.canonical)
    }

    @Test
    fun zonedCanonicalEncodesZoneThenLocalDateTime() {
        val key = Zoned(LocalDateTime(2026, 3, 14, 9, 30), "Europe/Zurich")
        assertEquals("Z:Europe/Zurich:2026-03-14T09:30", key.canonical)
    }

    @Test
    fun utcCanonicalEncodesInstant() {
        val key = Utc(Instant.parse("2026-03-14T08:30:00Z"))
        assertEquals("U:2026-03-14T08:30:00Z", key.canonical)
    }

    @Test
    fun toStringReturnsCanonicalAndIsNotOverriddenByDataClass() {
        val keys = listOf(
            AllDay(LocalDate(2026, 3, 14)),
            Floating(LocalDateTime(2026, 3, 14, 9, 30)),
            Zoned(LocalDateTime(2026, 3, 14, 9, 30), "Europe/Zurich"),
            Utc(Instant.parse("2026-03-14T08:30:00Z")),
        )
        for (key in keys) assertEquals(key.canonical, key.toString())
    }
}
