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
    /** Raw RFC 5545 date/date-time string (e.g. "20260526T100000Z" or "20260526T100000" when [dtStartTzid] is set). */
    val dtstart: String?,
    /**
     * IANA `TZID` parameter of `DTSTART` (RFC 5545 FORM #3, e.g. "Europe/Paris"). `null` when the value is a
     * `DATE`, a UTC `DATE-TIME` (Z suffix), or floating (no `TZID`, no `Z`).
     */
    val dtStartTzid: String?,
    /** Raw RFC 5545 date/date-time string (e.g. "20260526T110000Z" or "20260526T110000" when [dtEndTzid] is set). */
    val dtend: String?,
    /** IANA `TZID` parameter of `DTEND`. Same semantics as [dtStartTzid]. */
    val dtEndTzid: String?,
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
    /** Raw `X-APPLE-CALENDAR-COLOR` value (Apple extension, typically `#RRGGBB` or `#RRGGBBAA`). */
    val colorHex: String?,
    /** Raw `COLOR` value (RFC 7986 §5.9, a case-insensitive CSS3 color name). */
    val colorIcalName: String?,
    /** ATTENDEE participants parsed from the VEVENT. */
    val attendees: List<RemoteDavAttendee>,
    /** ORGANIZER of the VEVENT (RFC 5545 §3.8.4.3), independent from [attendees]. */
    val organizer: RemoteDavOrganizer? = null,
    val alarms: List<RemoteDavAlarm> = emptyList(),
)

