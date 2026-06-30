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

/** An event resource as returned by the CalDAV server, with parsed iCalendar fields. */
data class RemoteDavEvent(
    val url: String,
    val etag: String,
    /** Raw iCS data (needed for update/delete round-trips). */
    val icsData: String,
    /** Parsed VEVENT fields. */
    val uid: String,
    val summary: String?,
    val description: String?,
    val location: String?,
    /** Raw RFC 5545 date/date-time string (e.g. "20260526T100000Z"). */
    val dtstart: String?,
    /** Raw RFC 5545 date/date-time string (e.g. "20260526T110000Z"). */
    val dtend: String?,
    /**
     * Raw RFC 5545 `DURATION` value (e.g. "PT1H", "P2W"). Mutually exclusive with [dtend]:
     * a conformant VEVENT carries at most one of the two.
     */
    val duration: String?,
    /** Raw RFC 5545 date-time string of the event creation. */
    val created: String?,
    /** Raw RFC 5545 date-time string of the last modification. */
    val lastModified: String?,
    /** Raw RFC 5545 date-time string of the instance creation in the calendar store. */
    val dtstamp: String?,
    val rrule: String?,
    val status: String?,
    /** Time transparency: `OPAQUE` or `TRANSPARENT`. */
    val transp: String?,
    /** Access classification: `PUBLIC`, `PRIVATE` or `CONFIDENTIAL` (iCal `CLASS`). */
    val classification: String?,
    /** Raw priority (0-9). */
    val priority: String?,
    /** Raw revision sequence number. */
    val sequence: String?,
    /** Comma-separated categories. */
    val categories: String?,
    /** ORGANIZER + ATTENDEE participants parsed from the VEVENT. */
    val attendees: List<RemoteDavAttendee>,
)

