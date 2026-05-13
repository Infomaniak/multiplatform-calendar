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

package com.infomaniak.multiplatform_calendar.model.event.recurrenceRule

import kotlinx.datetime.DayOfWeek

/**
 * A day-of-week optionally qualified by an ordinal position
 * (e.g. `2MO` = second Monday, `-1FR` = last Friday).
 */
data class WeekDayNum(
    val ordinal: Int? = null,
    val dayOfWeek: DayOfWeek,
) {
    fun toRfc5545(): String {
        val dayCode = dayOfWeek.toCode()
        return if (ordinal != null) "$ordinal$dayCode" else dayCode
    }

    companion object {
        private val regex by lazy { Regex("^(-?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)$", RegexOption.IGNORE_CASE) }
        fun parse(token: String): WeekDayNum? {
            return regex.find(token)?.run {
                val (ordinalString, dayCode) = destructured
                dayCode.uppercase().toDayOfWeek()?.let { dow ->
                    ordinalString.toIntOrNull()?.let { ordinal ->
                        WeekDayNum(ordinal = ordinal, dayOfWeek = dow)
                    }
                }
            }
        }
    }
}

private fun String.toDayOfWeek(): DayOfWeek? = when (this) {
    "MO" -> DayOfWeek.MONDAY
    "TU" -> DayOfWeek.TUESDAY
    "WE" -> DayOfWeek.WEDNESDAY
    "TH" -> DayOfWeek.THURSDAY
    "FR" -> DayOfWeek.FRIDAY
    "SA" -> DayOfWeek.SATURDAY
    "SU" -> DayOfWeek.SUNDAY
    else -> null
}

private fun DayOfWeek.toCode(): String = when (this) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "TU"
    DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SU"
}
