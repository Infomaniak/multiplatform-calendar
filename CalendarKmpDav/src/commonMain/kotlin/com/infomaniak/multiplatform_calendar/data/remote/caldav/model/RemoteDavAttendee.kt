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
package com.infomaniak.multiplatform_calendar.data.remote.caldav.model

/** A single participant (ORGANIZER or ATTENDEE) parsed from a VEVENT. Raw iCal values. */
data class RemoteDavAttendee(
    val email: String,
    val displayName: String?,
    /** Raw `PARTSTAT` (e.g. "ACCEPTED", "NEEDS-ACTION"). */
    val status: String?,
    /** Raw `ROLE` (e.g. "REQ-PARTICIPANT", "OPT-PARTICIPANT"). */
    val role: String?,
    val isOrganizer: Boolean,
    /** `RSVP=TRUE`: a response is expected. */
    val responseNeeded: Boolean,
)
