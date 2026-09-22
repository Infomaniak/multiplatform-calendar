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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class OccurrenceRebaseTest {

    private val zurich = TimeZone.of("Europe/Zurich")
    private val newYork = TimeZone.of("America/New_York")

    @Test
    fun rebasedOnto_anUntouchedTime_leavesTheMasterWhereItStands() {
        val master = timing(LocalDateTime(2026, 6, 15, 10, 0), LocalDateTime(2026, 6, 15, 11, 0))
        // The app hands the occurrence's own slot back even when the edit was about something else.
        val edited = timing(LocalDateTime(2026, 6, 17, 10, 0), LocalDateTime(2026, 6, 17, 11, 0))

        val rebased = edited.rebasedOnto(master, shownStart = LocalDateTime(2026, 6, 17, 10, 0))

        assertEquals(LocalDateTime(2026, 6, 15, 10, 0), rebased.start)
        assertEquals(LocalDateTime(2026, 6, 15, 11, 0), rebased.end)
    }

    @Test
    fun rebasedOnto_aMovedOccurrence_shiftsTheMasterByThatMuch() {
        val master = timing(LocalDateTime(2026, 6, 15, 10, 0), LocalDateTime(2026, 6, 15, 11, 0))
        // The 17th was pushed two days on and four hours later, into a longer slot.
        val edited = timing(LocalDateTime(2026, 6, 19, 14, 0), LocalDateTime(2026, 6, 19, 15, 30))

        val rebased = edited.rebasedOnto(master, shownStart = LocalDateTime(2026, 6, 17, 10, 0))

        assertEquals(LocalDateTime(2026, 6, 17, 14, 0), rebased.start)
        // The duration is the edit's own, not the one the master used to have.
        assertEquals(LocalDateTime(2026, 6, 17, 15, 30), rebased.end)
    }

    @Test
    fun rebasedOnto_aShiftAcrossADstTransition_keepsTheWallClockTimeTheSeriesIsReadAt() {
        // Zurich moves to summer time on 29 March 2026, between the two slots below.
        val master = timing(LocalDateTime(2026, 1, 5, 10, 0), LocalDateTime(2026, 1, 5, 11, 0), zurich)
        // The occurrence shown on Saturday the 28th was dragged onto Monday the 30th, same hour.
        val edited = timing(LocalDateTime(2026, 3, 30, 10, 0), LocalDateTime(2026, 3, 30, 11, 0), zurich)

        val rebased = edited.rebasedOnto(master, shownStart = LocalDateTime(2026, 3, 28, 10, 0))

        // Measured in absolute time that drag is 47 hours, and would land the series on 09:00.
        assertEquals(LocalDateTime(2026, 1, 7, 10, 0), rebased.start)
        assertEquals(LocalDateTime(2026, 1, 7, 11, 0), rebased.end)
    }

    @Test
    fun rebasedOnto_aFloatingEvent_shiftsItWithoutAnchoringItAnywhere() {
        val master = timing(LocalDateTime(2026, 6, 15, 10, 0), LocalDateTime(2026, 6, 15, 11, 0), zone = null)
        val edited = timing(LocalDateTime(2026, 6, 17, 16, 0), LocalDateTime(2026, 6, 17, 17, 0), zone = null)

        val rebased = edited.rebasedOnto(master, shownStart = LocalDateTime(2026, 6, 17, 10, 0))

        assertEquals(LocalDateTime(2026, 6, 15, 16, 0), rebased.start)
        assertEquals(LocalDateTime(2026, 6, 15, 17, 0), rebased.end)
        assertEquals(null, rebased.startTimeZone)
    }

    @Test
    fun rebasedOnto_anAllDayEvent_movesItByWholeDays() {
        val master = timing(LocalDateTime(2026, 6, 15, 0, 0), LocalDateTime(2026, 6, 16, 0, 0), zone = null)
            .copy(isAllDay = true)
        val edited = timing(LocalDateTime(2026, 6, 19, 0, 0), LocalDateTime(2026, 6, 20, 0, 0), zone = null)
            .copy(isAllDay = true)

        val rebased = edited.rebasedOnto(master, shownStart = LocalDateTime(2026, 6, 17, 0, 0))

        assertEquals(LocalDateTime(2026, 6, 17, 0, 0), rebased.start)
        assertEquals(LocalDateTime(2026, 6, 18, 0, 0), rebased.end)
        assertEquals(true, rebased.isAllDay)
    }

    @Test
    fun rebasedOnto_endInAnotherZone_keepsTheZonesTheEditCameWith() {
        val master = timing(LocalDateTime(2026, 6, 15, 10, 0), LocalDateTime(2026, 6, 15, 8, 0), zurich)
            .copy(endTimeZone = newYork)
        // A flight: it leaves Zurich at 14:00 and lands in New York at 12:00 the same day.
        val edited = timing(LocalDateTime(2026, 6, 17, 14, 0), LocalDateTime(2026, 6, 17, 12, 0), zurich)
            .copy(endTimeZone = newYork)

        val rebased = edited.rebasedOnto(master, shownStart = LocalDateTime(2026, 6, 17, 10, 0))

        assertEquals(LocalDateTime(2026, 6, 15, 14, 0), rebased.start)
        // The faces are shifted as they stand, so the zone each one is read in is left untouched.
        assertEquals(LocalDateTime(2026, 6, 15, 12, 0), rebased.end)
        assertEquals(zurich, rebased.startTimeZone)
        assertEquals(newYork, rebased.endTimeZone)
    }

    @Test
    fun rebasedOnto_aSeriesEdit_keepsTheRuleTheEditCarries() {
        val master = timing(LocalDateTime(2026, 6, 15, 10, 0), LocalDateTime(2026, 6, 15, 11, 0))
        val rule = RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 4)
        val edited = timing(LocalDateTime(2026, 6, 17, 14, 0), LocalDateTime(2026, 6, 17, 15, 0))
            .copy(recurrenceRule = rule)

        val rebased = edited.rebasedOnto(master, shownStart = LocalDateTime(2026, 6, 17, 10, 0))

        assertEquals(rule, rebased.recurrenceRule)
    }

    private fun timing(
        start: LocalDateTime,
        end: LocalDateTime,
        zone: TimeZone? = TimeZone.UTC,
    ) = EventTiming(
        start = start,
        end = end,
        startTimeZone = zone,
        endTimeZone = zone,
        isAllDay = false,
    )
}
