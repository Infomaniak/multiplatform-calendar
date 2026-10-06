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

@Serializable
public enum class AttendeeRole(internal val icalValue: String) {
    /** The meeting chair, distinct from the organizer which is a separate `ORGANIZER` property. */
    Chair("CHAIR"),
    Requested("REQ-PARTICIPANT"),
    Optional("OPT-PARTICIPANT"),
    NonParticipant("NON-PARTICIPANT");

    internal companion object {
        /** [Requested] when absent or unrecognised, as RFC 5545 defines it. */
        fun fromIcal(raw: String?): AttendeeRole {
            return entries.firstOrNull { it.icalValue.equals(raw, ignoreCase = true) } ?: Requested
        }
    }
}
