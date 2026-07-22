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

package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.serialization.Serializable

/**
 * A day-of-week optionally qualified by an ordinal position
 * (e.g. `2MO` = second Monday, `-1FR` = last Friday).
 */
@Serializable
public data class WeekDayNum(
    val ordinal: Int? = null,
    val dayOfWeek: DayOfWeek,
) {
    internal fun toICalString(): String {
        val dayCode = dayOfWeek.toCode()
        return if (ordinal != null) "$ordinal$dayCode" else dayCode
    }

    internal companion object {
        private val regex by lazy { Regex("^(-?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)$", RegexOption.IGNORE_CASE) }
        internal fun parse(token: String): WeekDayNum? {
            return regex.find(token)?.run {
                val (ordinalString, dayCode) = destructured
                dayCode.uppercase().toDayOfWeek()?.let { dow ->
                    WeekDayNum(ordinal = ordinalString.toIntOrNull(), dayOfWeek = dow)
                }
            }
        }
    }
}

private fun String.toDayOfWeek(): DayOfWeek? = when (this) {
    "MO" -> MONDAY
    "TU" -> TUESDAY
    "WE" -> WEDNESDAY
    "TH" -> THURSDAY
    "FR" -> FRIDAY
    "SA" -> SATURDAY
    "SU" -> SUNDAY
    else -> null
}

private fun DayOfWeek.toCode(): String = when (this) {
    MONDAY -> "MO"
    TUESDAY -> "TU"
    WEDNESDAY -> "WE"
    THURSDAY -> "TH"
    FRIDAY -> "FR"
    SATURDAY -> "SA"
    SUNDAY -> "SU"
}
