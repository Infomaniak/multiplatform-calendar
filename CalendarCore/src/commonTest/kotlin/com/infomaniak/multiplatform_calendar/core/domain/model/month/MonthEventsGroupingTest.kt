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
package com.infomaniak.multiplatform_calendar.core.domain.model.month

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.MultiDayGrouping
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.allDayTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.groupDaySlicesByDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toSummary
import com.infomaniak.multiplatform_calendar.core.domain.model.event.zonedTiming
import com.infomaniak.multiplatform_calendar.core.utils.ColorComputation
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthEventsGroupingTest {

    private val june = YearMonth(2026, 6)
    private val july = YearMonth(2026, 7)

    @BeforeTest
    fun setUp() {
        ColorComputation.resetCache()
    }

    @Test
    fun monthGridDays_coversTheWholeWeeksOfTheMonths() {
        assertEquals(
            LocalDate(2026, 6, 1)..LocalDate(2026, 8, 2),
            monthWeekDays(june, july, DayOfWeek.MONDAY),
        )
        assertEquals(
            LocalDate(2026, 5, 31)..LocalDate(2026, 7, 4),
            monthWeekDays(june, june, DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun layOutMonths_listsTheWeeksTouchingEachMonth_sharingTheWeekAcrossTwoMonths() = runTest {
        val months = groupIntoMonthEvents(emptyList())

        val juneWeeks = months.getValue(june).weeks
        val julyWeeks = months.getValue(july).weeks
        assertEquals(
            listOf(
                LocalDate(2026, 6, 1),
                LocalDate(2026, 6, 8),
                LocalDate(2026, 6, 15),
                LocalDate(2026, 6, 22),
                LocalDate(2026, 6, 29),
            ),
            juneWeeks.map { it.days.first().date },
        )
        assertEquals(LocalDate(2026, 7, 5), juneWeeks.last().days.last().date)
        assertEquals(juneWeeks.last(), julyWeeks.first())
        assertEquals(5, julyWeeks.size)
    }

    @Test
    fun layOutMonths_putsMultiDaySegmentsFirst_eachOnTheLowestFreeLane() = runTest {
        val events = listOf(
            allDay(LocalDate(2026, 6, 9), LocalDate(2026, 6, 12), id = "event://a"),
            timed(LocalDateTime(2026, 6, 10, 9, 0), LocalDateTime(2026, 6, 10, 10, 0), id = "event://b"),
            timed(LocalDateTime(2026, 6, 8, 22, 0), LocalDateTime(2026, 6, 9, 2, 0), id = "event://c"),
            allDay(LocalDate(2026, 6, 11), LocalDate(2026, 6, 14), id = "event://d"),
        )

        val week = groupIntoMonthEvents(events).getValue(june).weeks[1]

        assertEquals(
            listOf(
                Placement("event://c", dayIndex = 0, span = 2, lane = 0),
                Placement("event://a", dayIndex = 1, span = 3, lane = 1),
                Placement("event://d", dayIndex = 3, span = 3, lane = 0),
                Placement("event://b", dayIndex = 2, span = 1, lane = 0),
            ),
            week.segments.map(::placementOf),
        )
    }

    @Test
    fun layOutMonths_splitsASegmentAtTheWeekStart() = runTest {
        val events = listOf(allDay(LocalDate(2026, 6, 12), LocalDate(2026, 6, 17), id = "event://across"))

        val weeks = groupIntoMonthEvents(events).getValue(june).weeks

        assertEquals(Placement("event://across", dayIndex = 4, span = 3, lane = 0), placementOf(weeks[1].segments.single()))
        assertEquals(Placement("event://across", dayIndex = 0, span = 2, lane = 0), placementOf(weeks[2].segments.single()))
    }

    @Test
    fun layOutMonths_countsEachDayEventsAndLanes_includingLanesLeftEmpty() = runTest {
        val events = listOf(
            allDay(LocalDate(2026, 6, 8), LocalDate(2026, 6, 10), id = "event://a"),
            allDay(LocalDate(2026, 6, 9), LocalDate(2026, 6, 11), id = "event://b"),
        )

        val week = groupIntoMonthEvents(events).getValue(june).weeks[1]

        assertEquals(
            listOf(1 to 1, 2 to 2, 1 to 2, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
            week.days.map { it.eventCount to it.laneCount },
        )
    }

    private suspend fun groupIntoMonthEvents(events: List<Event>): Map<YearMonth, MonthEvents> {
        val days = monthWeekDays(june, july, DayOfWeek.MONDAY)
        return events.map(Event::toSummary)
            .groupDaySlicesByDay(
                rangeStart = days.start.atStartOfDayIn(TimeZone.UTC),
                rangeEnd = days.endInclusive.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
                timeZone = TimeZone.UTC,
                grouping = MultiDayGrouping.AllEvents(DayOfWeek.MONDAY),
            )
            .groupIntoMonthEvents(june, july, DayOfWeek.MONDAY)
    }

    private fun placementOf(segment: WeekEventSegment) =
        Placement(segment.slice.event.occurrenceId.value, segment.dayIndex, segment.span, segment.lane)

    private fun timed(start: LocalDateTime, end: LocalDateTime, id: String) =
        eventOf(zonedTiming(start = start, end = end, zone = TimeZone.UTC), id)

    private fun allDay(start: LocalDate, endExclusive: LocalDate, id: String) =
        eventOf(allDayTiming(start = start, end = endExclusive), id)

    private fun eventOf(timing: EventTiming, id: String): Event = Event(
        masterEventId = EventId(id),
        occurrenceId = OccurrenceId.Master(EventId(id)),
        calendarId = CalendarId("calendar://test"),
        accountId = AccountId(1L),
        title = "Test",
        timing = timing,
        colors = EventColors.from(eventSourceColor = 0xFF2196F3.toInt(), calendarSourceColor = 0xFF2196F3.toInt()),
        canEdit = true,
    )

    private data class Placement(val id: String, val dayIndex: Int, val span: Int, val lane: Int)
}
