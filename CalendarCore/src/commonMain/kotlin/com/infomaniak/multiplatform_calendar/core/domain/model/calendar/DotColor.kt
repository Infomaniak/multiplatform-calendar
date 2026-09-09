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
package com.infomaniak.multiplatform_calendar.core.domain.model.calendar

/**
 * One dot of a day cell: the color of a calendar owning at least one event that day. Two calendars sharing
 * a color get a dot each, so a day list may hold equal values and cannot serve as a set of stable keys.
 */
public data class DotColor(
    val sourceColor: Int,
) {
    public companion object {

        /** The calendar's color, or [CalendarColors.DEFAULT_SOURCE_COLOR] when it declares none. */
        public fun from(calendarColorArgb: Int?): DotColor {
            return DotColor(calendarColorArgb ?: CalendarColors.DEFAULT_SOURCE_COLOR)
        }
    }
}

