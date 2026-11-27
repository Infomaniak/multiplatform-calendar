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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.MultiDayGrouping
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventDaySlice
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

/** The days of every week touched by the months from [startMonth] to [endMonth]. */
internal fun monthWeekDays(startMonth: YearMonth, endMonth: YearMonth, weekStart: DayOfWeek): ClosedRange<LocalDate> =
    startMonth.firstDay.startOfWeek(weekStart)..endMonth.lastDay.startOfWeek(weekStart).plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)

/**
 * Splits these slices, grouped by [MultiDayGrouping.AllEvents] on the same [weekStart], into the weeks of the months
 * from [startMonth] to [endMonth].
 *
 * In each week, multi-day segments come first, by first day then longest first; single-day ones follow in their
 * day's order. Each segment takes the lowest lane free on all its days.
 */
internal suspend fun Map<LocalDate, List<EventDaySlice>>.groupIntoMonthEvents(
    startMonth: YearMonth,
    endMonth: YearMonth,
    weekStart: DayOfWeek,
): Map<YearMonth, MonthEvents> {
    val weeksByStart = mutableMapOf<LocalDate, WeekEvents>()
    return (startMonth..endMonth).associateWith { month ->
        val weeks = generateSequence(month.firstDay.startOfWeek(weekStart)) { it.plus(1, DateTimeUnit.WEEK) }
            .takeWhile { it <= month.lastDay }
            .toList()
            .map { start -> weeksByStart.getOrPut(start) { weekEventsFrom(start) } }
        MonthEvents(month, weeks)
    }
}

private suspend fun Map<LocalDate, List<EventDaySlice>>.weekEventsFrom(start: LocalDate): WeekEvents {
    currentCoroutineContext().ensureActive()
    val dates = List(DAYS_PER_WEEK) { start.plus(it, DateTimeUnit.DAY) }
    val lanes = WeekLanes()
    val segments = slicesInPlacementOrder(dates).map { (dayIndex, slice) ->
        WeekEventSegment(slice, dayIndex, lane = lanes.take(dayIndex until dayIndex + slice.dayCount))
    }
    val days = dates.mapIndexed { dayIndex, date -> WeekDay(date, lanes.eventCountOn(dayIndex), lanes.laneCountOn(dayIndex)) }
    return WeekEvents(days, segments)
}

/** Multi-day slices first, by first day; single-day ones follow in their day's order. */
private fun Map<LocalDate, List<EventDaySlice>>.slicesInPlacementOrder(dates: List<LocalDate>): List<Pair<Int, EventDaySlice>> =
    dates.flatMapIndexed { dayIndex, date -> get(date).orEmpty().map { dayIndex to it } }
        .sortedByDescending { (_, slice) -> slice.dayCount > 1 }

private fun LocalDate.startOfWeek(weekStart: DayOfWeek): LocalDate =
    plus(-(dayOfWeek.ordinal - weekStart.ordinal).mod(DAYS_PER_WEEK), DateTimeUnit.DAY)

private val DAYS_PER_WEEK = DayOfWeek.entries.size
