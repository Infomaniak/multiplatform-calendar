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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteCalendarEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteSyncCollectionResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * Remote CalDAV data source backed by the Rust `caldav_bridge` native library.
 *
 * Every method talks to the server via `libdav` (Rust) over an FFI boundary.
 * The caller never sees JSON — it works with domain types directly.
 */
interface CalendarSyncRemoteSource {

    /** Discover all calendars for the given credentials. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun discoverCalendars(credentials: DavAccount): List<RemoteDavCalendar>

    /** Update editable CalDAV properties on a calendar collection (PROPPATCH). No-op if [edit] has no changes. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun updateCalendar(credentials: DavAccount, calendarUrl: String, edit: RemoteCalendarEdit)

    /** Fetch all events (iCalendar resources) inside a calendar. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun getEvents(credentials: DavAccount, calendarUrl: String): List<RemoteDavEvent>

    /** Fetch only VEVENT resources overlapping the UTC iCal range [start, end]. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun getEventsInRange(
        credentials: DavAccount,
        calendarUrl: String,
        start: String,
        end: String,
    ): List<RemoteDavEvent>

    /** Sync a calendar incrementally. [syncToken] can be null for a first full sync pass. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun syncCollection(
        credentials: DavAccount,
        calendarUrl: String,
        syncToken: String?,
    ): RemoteSyncCollectionResult

    /** Fetch a specific list of event URLs (href) using CalDAV calendar-multiget. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun getEventsByUrls(
        credentials: DavAccount,
        calendarUrl: String,
        eventUrls: List<String>,
    ): List<RemoteDavEvent>

    /** Apply [edit] onto an existing iCS, returning the re-serialized iCalendar object (no network). */
    suspend fun patchEventIcs(icsData: String, edit: RemoteEventEdit): String

    /** Build a fresh iCS (one VEVENT, new UID) from [edit] (no network). */
    suspend fun buildEventIcs(edit: RemoteEventEdit): String

    /** Create a new event. Returns the server-assigned URL + etag. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun createEvent(credentials: DavAccount, calendarUrl: String, icsData: String): RemoteDavEventRef

    /** Update an existing event (identified by its URL + etag for conflict detection). */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun updateEvent(credentials: DavAccount, eventUrl: String, etag: String, icsData: String): RemoteDavEventRef

    /** Delete an event (identified by its URL + etag). */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String)
}

