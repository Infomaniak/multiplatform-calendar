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
package com.infomaniak.multiplatform_calendar.caldav.data.remote

import com.infomaniak.multiplatform_calendar.caldav.data.remote.model.CaldavCredentials
import com.infomaniak.multiplatform_calendar.caldav.data.remote.model.RemoteCalendar
import com.infomaniak.multiplatform_calendar.caldav.data.remote.model.RemoteEvent
import com.infomaniak.multiplatform_calendar.caldav.data.remote.model.RemoteEventRef

/**
 * Remote CalDAV data source backed by the Rust `caldav_bridge` native library.
 *
 * Every method talks to the server via `libdav` (Rust) over an FFI boundary.
 * The caller never sees JSON — it works with domain types directly.
 */
interface CaldavClient {

    /** Discover all calendars for the given credentials. */
    suspend fun discoverCalendars(credentials: CaldavCredentials): List<RemoteCalendar>

    /** Fetch all events (iCalendar resources) inside a calendar. */
    suspend fun getEvents(credentials: CaldavCredentials, calendarUrl: String): List<RemoteEvent>

    /** Create a new event. Returns the server-assigned URL + etag. */
    suspend fun createEvent(credentials: CaldavCredentials, calendarUrl: String, icsData: String): RemoteEventRef

    /** Update an existing event (identified by its URL + etag for conflict detection). */
    suspend fun updateEvent(credentials: CaldavCredentials, eventUrl: String, etag: String, icsData: String): RemoteEventRef

    /** Delete an event (identified by its URL + etag). */
    suspend fun deleteEvent(credentials: CaldavCredentials, eventUrl: String, etag: String)
}

