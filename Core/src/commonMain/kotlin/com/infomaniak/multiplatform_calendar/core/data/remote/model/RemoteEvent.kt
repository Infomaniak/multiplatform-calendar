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
package com.infomaniak.multiplatform_calendar.core.data.remote.model

/** An event resource as returned by the CalDAV server, with parsed iCalendar fields. */
data class RemoteEvent(
    val url: String,
    val etag: String,
    /** Raw iCS data (needed for update/delete round-trips). */
    val icsData: String,
    /** Parsed VEVENT fields. */
    val uid: String,
    val summary: String?,
    val description: String?,
    val location: String?,
    /** ISO 8601 date-time string (e.g. "20260526T100000Z"). */
    val dtstart: String?,
    /** ISO 8601 date-time string (e.g. "20260526T110000Z"). */
    val dtend: String?,
    val rrule: String?,
    val status: String?,
)

