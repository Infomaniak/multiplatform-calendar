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

import kotlinx.serialization.Serializable

/** What an attendee is (iCal `CUTYPE`, RFC 5545 §3.2.3). */
@Serializable
public enum class AttendeeType(internal val icalValue: String) {
    Individual("INDIVIDUAL"),
    Group("GROUP"),
    Resource("RESOURCE"),
    Room("ROOM"),
    Unknown("UNKNOWN");

    internal companion object {
        /** [Individual] when absent, [Unknown] when unrecognised, as RFC 5545 defines it. */
        fun fromIcal(raw: String?): AttendeeType {
            if (raw == null) return Individual
            return entries.firstOrNull { it.icalValue.equals(raw, ignoreCase = true) } ?: Unknown
        }
    }
}
