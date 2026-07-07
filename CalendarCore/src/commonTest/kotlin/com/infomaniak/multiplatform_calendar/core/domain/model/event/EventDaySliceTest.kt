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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EventDaySliceTest {

    private val paris = TimeZone.of("Europe/Paris")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val newYork = TimeZone.of("America/New_York")

    private val wideWindow = LocalDate(2026, 1, 1)..LocalDate(2026, 3, 31)

    // ---- Single day ----------------------------------------------------------------------------

    @Test
    fun singleDayTimed_yieldsOneSlice() = runTest {
        val slices = timed(
            start = LocalDateTime(2026, 1, 5, 14, 0),
            end = LocalDateTime(2026, 1, 5, 15, 30),
            zone = paris,
        ).expandDaySlices(wideWindow, paris)

        assertEquals(1, slices.size)
        val slice = slices.single()
        assertEquals(LocalDate(2026, 1, 5), slice.date)
        assertEquals(LocalDateTime(2026, 1, 5, 14, 0), slice.displayStart)
        assertEquals(LocalDateTime(2026, 1, 5, 15, 30), slice.displayEnd)
        assertEquals(0, slice.position.index)
        assertEquals(1, slice.position.count)
        assertTrue(slice.isFirstDay)
        assertTrue(slice.isLastDay)
        assertFalse(slice.isAllDay)
        assertFalse(slice.fillsWholeDay)
    }

    @Test
    fun zeroLengthTimed_yieldsOneSlice() = runTest {
        val slices = timed(
            start = LocalDateTime(2026, 1, 5, 9, 0),
            end = LocalDateTime(2026, 1, 5, 9, 0),
            zone = paris,
        ).expandDaySlices(wideWindow, paris)

        val slice = slices.single()
        assertEquals(slice.displayStart, slice.displayEnd)
        assertEquals(1, slice.position.count)
    }

    // ---- Multi-day timed -----------------------------------------------------------------------

    @Test
    fun multiDayTimed_splitsPerDay_withAbsoluteIndicesAndClampedBounds() = runTest {
        val slices = timed(
            start = LocalDateTime(2026, 1, 5, 14, 0),
            end = LocalDateTime(2026, 1, 7, 10, 0),
            zone = paris,
        ).expandDaySlices(wideWindow, paris)

        assertEquals(3, slices.size)
        assertEquals(listOf(0, 1, 2), slices.map { it.position.index })
        assertTrue(slices.all { it.position.count == 3 })
        // Human-friendly labels, e.g. "1/3", "2/3", "3/3".
        assertEquals(listOf("1/3", "2/3", "3/3"), slices.map { it.position.toString() })

        val (first, middle, last) = slices
        // First day: real start → midnight.
        assertEquals(LocalDateTime(2026, 1, 5, 14, 0), first.displayStart)
        assertEquals(LocalDateTime(2026, 1, 6, 0, 0), first.displayEnd)
        assertTrue(first.isFirstDay)
        assertFalse(first.isLastDay)
        assertFalse(first.fillsWholeDay)

        // Middle day: full 00:00 → next 00:00, fills the whole day but is NOT an all-day event.
        assertEquals(LocalDateTime(2026, 1, 6, 0, 0), middle.displayStart)
        assertEquals(LocalDateTime(2026, 1, 7, 0, 0), middle.displayEnd)
        assertTrue(middle.fillsWholeDay)
        assertFalse(middle.isAllDay)

        // Last day: midnight → real end.
        assertEquals(LocalDateTime(2026, 1, 7, 0, 0), last.displayStart)
        assertEquals(LocalDateTime(2026, 1, 7, 10, 0), last.displayEnd)
        assertTrue(last.isLastDay)
    }

    @Test
    fun endExactlyOnMidnight_doesNotSpillPhantomSlice() = runTest {
        val slices = timed(
            start = LocalDateTime(2026, 1, 5, 22, 0),
            end = LocalDateTime(2026, 1, 6, 0, 0),
            zone = paris,
        ).expandDaySlices(wideWindow, paris)

        val slice = slices.single()
        assertEquals(LocalDate(2026, 1, 5), slice.date)
        assertEquals(LocalDateTime(2026, 1, 5, 22, 0), slice.displayStart)
        assertEquals(LocalDateTime(2026, 1, 6, 0, 0), slice.displayEnd)
    }

    // ---- All-day -------------------------------------------------------------------------------

    @Test
    fun allDaySingle_isAllDay_fillsWholeDay() = runTest {
        val slices = allDay(
            start = LocalDate(2026, 1, 5),
            endExclusive = LocalDate(2026, 1, 6),
        ).expandDaySlices(wideWindow, paris)

        val slice = slices.single()
        assertTrue(slice.isAllDay)
        assertTrue(slice.fillsWholeDay)
        assertEquals(1, slice.position.count)
    }

    @Test
    fun allDayMultiDay_splitsPerDay() = runTest {
        val slices = allDay(
            start = LocalDate(2026, 1, 5),
            endExclusive = LocalDate(2026, 1, 8),
        ).expandDaySlices(wideWindow, paris)

        assertEquals(listOf(LocalDate(2026, 1, 5), LocalDate(2026, 1, 6), LocalDate(2026, 1, 7)), slices.map { it.date })
        assertTrue(slices.all { it.isAllDay && it.fillsWholeDay && it.position.count == 3 })
    }

    // ---- Floating & cross-zone -----------------------------------------------------------------

    @Test
    fun floatingMultiDay_isExpandedOnItsWallClock_regardlessOfGridZone() = runTest {
        // Floating (no zone): wall-clock is taken as-is in the grid, per RFC 5545 FORM #1.
        val slices = EventTiming(
            start = LocalDateTime(2026, 1, 5, 22, 0),
            end = LocalDateTime(2026, 1, 6, 2, 0),
            startTimeZone = null,
            endTimeZone = null,
            isAllDay = false,
        ).let(::eventOf).expandDaySlices(wideWindow, tokyo)

        assertEquals(2, slices.size)
        assertEquals(LocalDateTime(2026, 1, 5, 22, 0), slices[0].displayStart)
        assertEquals(LocalDateTime(2026, 1, 6, 0, 0), slices[0].displayEnd)
        assertEquals(LocalDateTime(2026, 1, 6, 0, 0), slices[1].displayStart)
        assertEquals(LocalDateTime(2026, 1, 6, 2, 0), slices[1].displayEnd)
    }

    @Test
    fun crossZoneFlight_usesPerSideZonesWhenReprojecting() = runTest {
        // 09:00 New York (UTC-5) → 21:00 Paris (UTC+1), both in January.
        val slices = EventTiming(
            start = LocalDateTime(2026, 1, 5, 9, 0),
            end = LocalDateTime(2026, 1, 5, 21, 0),
            startTimeZone = newYork,
            endTimeZone = paris,
            isAllDay = false,
        ).let(::eventOf).expandDaySlices(wideWindow, paris)

        val slice = slices.single()
        // 09:00 NY == 15:00 Paris; end already in Paris.
        assertEquals(LocalDateTime(2026, 1, 5, 15, 0), slice.displayStart)
        assertEquals(LocalDateTime(2026, 1, 5, 21, 0), slice.displayEnd)
    }

    // ---- Window clamping & DST -----------------------------------------------------------------

    @Test
    fun windowSmallerThanEvent_clampsSlicesButKeepsAbsoluteIndices() = runTest {
        val slices = allDay(
            start = LocalDate(2026, 1, 5),
            endExclusive = LocalDate(2026, 1, 10), // 5 days: 5,6,7,8,9
        ).expandDaySlices(LocalDate(2026, 1, 6)..LocalDate(2026, 1, 7), paris)

        assertEquals(listOf(LocalDate(2026, 1, 6), LocalDate(2026, 1, 7)), slices.map { it.date })
        // Indices stay absolute to the event (day 6 is the event's 2nd day), not window-relative.
        assertEquals(listOf(1, 2), slices.map { it.position.index })
        assertTrue(slices.all { it.position.count == 5 })
        assertFalse(slices.any { it.isFirstDay || it.isLastDay })
    }

    @Test
    fun dstSpringForward_dayArithmeticIsUnaffected() = runTest {
        // Europe/Paris springs forward on 2026-03-29 (02:00 → 03:00); the day is only 23h long.
        val slices = timed(
            start = LocalDateTime(2026, 3, 28, 23, 0),
            end = LocalDateTime(2026, 3, 30, 1, 0),
            zone = paris,
        ).expandDaySlices(wideWindow, paris)

        assertEquals(
            listOf(LocalDate(2026, 3, 28), LocalDate(2026, 3, 29), LocalDate(2026, 3, 30)),
            slices.map { it.date },
        )
        // The short DST day is still a full-day continuation slice.
        assertTrue(slices[1].fillsWholeDay)
    }

    // ---- Grouping / sorting (groupDaySlicesByDay) ----------------------------------------------

    @Test
    fun groupDaySlicesByDay_groupsAscending_andMultiDayEventAppearsInEachDay() = runTest {
        val events = listOf(
            timed(LocalDateTime(2026, 1, 5, 14, 0), LocalDateTime(2026, 1, 7, 10, 0), paris, id = "event://timed"),
            allDay(LocalDate(2026, 1, 6), LocalDate(2026, 1, 7), id = "event://allday"),
        )

        val byDay = events.groupDaySlicesByDay(
            rangeStart = parisInstant(2026, 1, 5, 0, 0),
            rangeEnd = parisInstant(2026, 1, 8, 0, 0),
            timeZone = paris,
        )

        assertEquals(listOf(LocalDate(2026, 1, 5), LocalDate(2026, 1, 6), LocalDate(2026, 1, 7)), byDay.keys.toList())
        assertEquals(1, byDay.getValue(LocalDate(2026, 1, 5)).size)
        // Day 6: the all-day event sorts before the timed continuation slice.
        val day6 = byDay.getValue(LocalDate(2026, 1, 6))
        assertEquals(2, day6.size)
        assertTrue(day6[0].isAllDay)
        assertFalse(day6[1].isAllDay)
        assertEquals(1, byDay.getValue(LocalDate(2026, 1, 7)).size)
    }

    @Test
    fun groupDaySlicesByDay_withinDay_allDayFirstThenTimedByStart() = runTest {
        val events = listOf(
            timed(LocalDateTime(2026, 1, 5, 14, 0), LocalDateTime(2026, 1, 5, 15, 0), paris, id = "event://afternoon"),
            allDay(LocalDate(2026, 1, 5), LocalDate(2026, 1, 6), id = "event://allday"),
            timed(LocalDateTime(2026, 1, 5, 9, 0), LocalDateTime(2026, 1, 5, 10, 0), paris, id = "event://morning"),
        )

        val day = events.groupDaySlicesByDay(
            rangeStart = parisInstant(2026, 1, 5, 0, 0),
            rangeEnd = parisInstant(2026, 1, 6, 0, 0),
            timeZone = paris,
        ).getValue(LocalDate(2026, 1, 5))

        assertEquals(
            listOf("event://allday", "event://morning", "event://afternoon"),
            day.map { it.event.id.url },
        )
    }

    @Test
    fun groupDaySlicesByDay_tieBreaksByEventIdUrl() = runTest {
        val events = listOf(
            timed(LocalDateTime(2026, 1, 5, 10, 0), LocalDateTime(2026, 1, 5, 11, 0), paris, id = "event://b"),
            timed(LocalDateTime(2026, 1, 5, 10, 0), LocalDateTime(2026, 1, 5, 11, 0), paris, id = "event://a"),
        )

        val day = events.groupDaySlicesByDay(
            rangeStart = parisInstant(2026, 1, 5, 0, 0),
            rangeEnd = parisInstant(2026, 1, 6, 0, 0),
            timeZone = paris,
        ).getValue(LocalDate(2026, 1, 5))

        assertEquals(listOf("event://a", "event://b"), day.map { it.event.id.url })
    }

    @Test
    fun groupDaySlicesByDay_rangeEndOnMidnight_excludesFollowingDay() = runTest {
        val events = listOf(
            allDay(LocalDate(2026, 1, 5), LocalDate(2026, 1, 6), id = "event://day5"),
            allDay(LocalDate(2026, 1, 6), LocalDate(2026, 1, 7), id = "event://day6"),
        )

        val byDay = events.groupDaySlicesByDay(
            rangeStart = parisInstant(2026, 1, 5, 0, 0),
            rangeEnd = parisInstant(2026, 1, 6, 0, 0), // exclusive: day 6 must not appear
            timeZone = paris,
        )

        assertEquals(listOf(LocalDate(2026, 1, 5)), byDay.keys.toList())
    }

    // ---- Cancellation --------------------------------------------------------------------------

    @Test
    fun groupDaySlicesByDay_cooperatesWithCancellation_beforeEachEvent() = runTest {
        val events = List(50) { allDay(LocalDate(2026, 1, 5), LocalDate(2026, 1, 6), id = "event://$it") }

        var completed = false
        // Swallow the CancellationException coroutineScope re-raises at completion; the point of the
        // test is whether groupDaySlicesByDay bailed *before* finishing (completed stays false).
        runCatching {
            coroutineScope {
                coroutineContext.job.cancel() // already cancelled: the per-event ensureActive() must trip
                events.groupDaySlicesByDay(
                    rangeStart = parisInstant(2026, 1, 5, 0, 0),
                    rangeEnd = parisInstant(2026, 1, 6, 0, 0),
                    timeZone = paris,
                )
                completed = true
            }
        }

        assertFalse(completed, "groupDaySlicesByDay should abort on cancellation, not run to completion")
    }

    @Test
    fun expandDaySlices_cooperatesWithCancellation_insideDayLoop() = runTest {
        // A single long event: only the inner while-loop guard can catch cancellation here.
        val longEvent = timed(LocalDateTime(2026, 1, 1, 8, 0), LocalDateTime(2026, 3, 31, 18, 0), paris)

        var completed = false
        runCatching {
            coroutineScope {
                coroutineContext.job.cancel()
                longEvent.expandDaySlices(wideWindow, paris)
                completed = true
            }
        }

        assertFalse(completed, "expandDaySlices should abort inside the day loop on cancellation")
    }

    // ---- lastInclusiveDay -----------------------------------------------------------------------

    @Test
    fun lastInclusiveDay_midnightOnLaterDay_returnsPreviousDay() {
        // DTEND is exclusive: midnight belongs to the day before.
        val end = LocalDateTime(2026, 1, 7, 0, 0)
        assertEquals(LocalDate(2026, 1, 6), end.lastInclusiveDay(notBefore = LocalDate(2026, 1, 5)))
    }

    @Test
    fun lastInclusiveDay_midnightOnStartDay_isClampedToNotBefore() {
        // Zero-length span at midnight: don't roll back below the start day.
        val end = LocalDateTime(2026, 1, 5, 0, 0)
        assertEquals(LocalDate(2026, 1, 5), end.lastInclusiveDay(notBefore = LocalDate(2026, 1, 5)))
    }

    @Test
    fun lastInclusiveDay_nonMidnightEnd_keepsEndDay() {
        val end = LocalDateTime(2026, 1, 6, 14, 0)
        assertEquals(LocalDate(2026, 1, 6), end.lastInclusiveDay(notBefore = LocalDate(2026, 1, 5)))
    }

    @Test
    fun lastInclusiveDay_endBeforeStart_isClampedToNotBefore() {
        // Corrupted data (DTEND < DTSTART): never go below the start day.
        val end = LocalDateTime(2026, 1, 3, 9, 0)
        assertEquals(LocalDate(2026, 1, 5), end.lastInclusiveDay(notBefore = LocalDate(2026, 1, 5)))
    }

    // ---- Builders ------------------------------------------------------------------------------

    private fun parisInstant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime(year, month, day, hour, minute).toInstant(paris)

    private fun timed(start: LocalDateTime, end: LocalDateTime, zone: TimeZone, id: String = "event://test") = eventOf(
        EventTiming(start = start, end = end, startTimeZone = zone, endTimeZone = zone, isAllDay = false),
        id = id,
    )

    private fun allDay(start: LocalDate, endExclusive: LocalDate, id: String = "event://test") = eventOf(
        EventTiming(
            start = LocalDateTime(start, LocalTime(0, 0)),
            end = LocalDateTime(endExclusive, LocalTime(0, 0)),
            startTimeZone = null,
            endTimeZone = null,
            isAllDay = true,
        ),
        id = id,
    )

    private fun eventOf(timing: EventTiming, id: String = "event://test"): Event = EventImpl(
        id = EventId(id),
        calendarId = CalendarId("calendar://test"),
        accountId = AccountId(1L),
        title = "Test",
        timing = timing,
        calendarColor = CalendarColor(0xFF2196F3.toInt()),
        colors = EventColors.from(0xFF2196F3.toInt()),
        canEdit = true,
    )
}
