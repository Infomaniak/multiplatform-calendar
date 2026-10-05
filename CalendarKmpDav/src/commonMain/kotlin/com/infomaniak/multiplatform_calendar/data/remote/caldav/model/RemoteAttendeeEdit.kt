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
package com.infomaniak.multiplatform_calendar.data.remote.caldav.model

/** One attendee of [RemoteAttendeesChange.Set]. Mirrors the Rust `AttendeeEdit` enum at the FFI boundary. */
sealed interface RemoteAttendeeEdit {
    val email: String

    /** Keep the stored line verbatim, every parameter included. */
    data class Kept(override val email: String) : RemoteAttendeeEdit

    /**
     * Write the given parameters onto the stored line, or add a line awaiting a response.
     * [role] and [userType] are the raw `ROLE` and `CUTYPE`; `null` leaves them as stored.
     */
    data class Written(
        override val email: String,
        val displayName: RemoteNameChange,
        val role: String?,
        val userType: String?,
    ) : RemoteAttendeeEdit
}
