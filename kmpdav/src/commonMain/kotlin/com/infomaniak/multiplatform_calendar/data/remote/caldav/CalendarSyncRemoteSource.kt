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
package com.infomaniak.multiplatform_calendar.data.remote.caldav

import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef

/**
 * Remote CalDAV data source backed by the Rust `caldav_bridge` native library.
 *
 * Every method talks to the server via `libdav` (Rust) over an FFI boundary.
 * The caller never sees JSON — it works with domain types directly.
 */
interface CalendarSyncRemoteSource {

    /** Discover all calendars for the given credentials. */
    suspend fun discoverCalendars(credentials: DavAccount): List<RemoteDavCalendar>

    /** Fetch all events (iCalendar resources) inside a calendar. */
    suspend fun getEvents(credentials: DavAccount, calendarUrl: String): List<RemoteDavEvent>

    /** Create a new event. Returns the server-assigned URL + etag. */
    suspend fun createEvent(credentials: DavAccount, calendarUrl: String, icsData: String): RemoteDavEventRef

    /** Update an existing event (identified by its URL + etag for conflict detection). */
    suspend fun updateEvent(credentials: DavAccount, eventUrl: String, etag: String, icsData: String): RemoteDavEventRef

    /** Delete an event (identified by its URL + etag). */
    suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String)
}

