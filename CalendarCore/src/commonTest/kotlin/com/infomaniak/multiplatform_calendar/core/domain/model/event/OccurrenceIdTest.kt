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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class OccurrenceIdTest {

    private val eventId = EventId("https://cal/main/series.ics")

    @Test
    fun parse_readsBackEveryFormItHandsOut() {
        val ids = listOf(
            OccurrenceId.Master(eventId),
            OccurrenceId.Recurrence(eventId, RecurrenceKey.AllDay(LocalDate(2026, 6, 16))),
            OccurrenceId.Recurrence(eventId, RecurrenceKey.Floating(LocalDateTime(2026, 6, 16, 10, 0))),
            OccurrenceId.Recurrence(eventId, RecurrenceKey.Zoned(LocalDateTime(2026, 6, 16, 10, 0), "Europe/Zurich")),
            OccurrenceId.Recurrence(eventId, RecurrenceKey.Utc(Instant.parse("2026-06-16T10:00:00Z"))),
        )

        ids.forEach { assertEquals(it, OccurrenceId.parse(it.value), "for $it") }
    }

    @Test
    fun parse_readsBackAZoneWhoseIdCarriesColonsOfItsOwn() {
        val id = OccurrenceId.Recurrence(eventId, RecurrenceKey.Zoned(LocalDateTime(2026, 6, 16, 10, 0), "UTC+01:30"))

        assertEquals(id, OccurrenceId.parse(id.value))
    }

    @Test
    fun parse_leavesAnEventUrlWithAFragmentWhole() {
        // Nothing announces an instance here.
        val url = "https://cal/main/series.ics#section"

        assertEquals(OccurrenceId.Master(EventId(url)), OccurrenceId.parse(url))
    }

    @Test
    fun parse_leavesWholeAResourceWhoseFragmentReadsLikeAKey() {
        val url = "https://cal/main/series.ics#AllDay:2026-06-16"

        assertEquals(
            OccurrenceId.Master(EventId(url)),
            OccurrenceId.parse(url),
            "the fragment names this resource, not an instance of another one",
        )
    }

    @Test
    fun parse_readsBackAMasterUrlCarryingSeparatorsOfItsOwn() {
        val master = EventId("https://cal/main/odd#name#more.ics")
        val id = OccurrenceId.Recurrence(master, RecurrenceKey.AllDay(LocalDate(2026, 6, 16)))

        assertEquals(id, OccurrenceId.parse(id.value))
    }
}
