/*
 * Infomaniak Core - Android
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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule

import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Serializable
public data class RecurrenceRule(
    val freq: Frequency,
    val interval: Int = 1,
    val occurrenceCount: Int? = null,
    val until: Instant? = null,
    val byDay: List<WeekDayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    // Selects occurrences by position within the set produced by the other BY* rules; negatives count from the end.
    val byOccurrencePosition: List<Int> = emptyList(),
    val weekStart: DayOfWeek? = null,
    val byHour: List<Int> = emptyList(),
    val byMinute: List<Int> = emptyList(),
    val bySecond: List<Int> = emptyList(),
    val byYearDay: List<Int> = emptyList(),
    val byWeekNumber: List<Int> = emptyList(),
)
