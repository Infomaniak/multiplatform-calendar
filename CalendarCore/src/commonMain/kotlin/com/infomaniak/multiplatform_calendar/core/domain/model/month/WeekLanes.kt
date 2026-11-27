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

import kotlinx.datetime.DayOfWeek

internal class WeekLanes {
    private val occupied = List(DAYS_PER_WEEK) { mutableSetOf<Int>() }
    private val lowestFree = IntArray(DAYS_PER_WEEK)

    /** Takes the lowest lane free on all [days]. */
    fun take(days: IntRange): Int {
        val lane = generateSequence(days.maxOf { lowestFree[it] }, Int::inc)
            .first { lane -> days.none { lane in occupied[it] } }
        days.forEach { day -> occupy(day, lane) }
        return lane
    }

    fun eventCountOn(day: Int): Int = occupied[day].size

    fun laneCountOn(day: Int): Int = occupied[day].maxOrNull()?.plus(1) ?: 0

    private fun occupy(day: Int, lane: Int) {
        occupied[day] += lane
        advanceLowestFree(day)
    }

    /** Lanes are never released, so a day's lowest free lane only moves up. */
    private fun advanceLowestFree(day: Int) {
        while (lowestFree[day] in occupied[day]) lowestFree[day]++
    }
}

private val DAYS_PER_WEEK = DayOfWeek.entries.size
