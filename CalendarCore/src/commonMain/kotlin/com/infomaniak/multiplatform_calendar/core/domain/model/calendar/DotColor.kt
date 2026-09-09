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
 * One dot of a day cell, reduced per calendar **and** per color: an event redefining its color (RFC 7986
 * `COLOR`) gets its own dot, and two calendars sharing a color get a dot each. A day list may therefore
 * hold equal values, so it cannot serve as a set of stable keys.
 */
public data class DotColor(
    val sourceColor: Int,
) {
    public companion object {

        /** The event's own color when it declares one, its calendar's otherwise. */
        public fun from(eventColorArgb: Int?, calendarColorArgb: Int?): DotColor {
            return DotColor(eventColorArgb ?: calendarColorArgb ?: CalendarColors.DEFAULT_SOURCE_COLOR)
        }
    }
}

