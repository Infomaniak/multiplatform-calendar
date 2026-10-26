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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavDiscovery
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventSyncDelta
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteVeventSeed
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteReplyScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Remote CalDAV data source backed by the Rust `caldav_bridge` native library.
 *
 * Every method talks to the server via `libdav` (Rust) over an FFI boundary.
 * The caller never sees JSON — it works with domain types directly.
 */
interface CalendarSyncRemoteSource {

    /** Discover the user's emails and all calendars for the given credentials. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun discover(credentials: DavAccount): RemoteDavDiscovery

    /** Update editable CalDAV properties on a calendar collection (PROPPATCH). No-op if [edit] has no changes. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun updateCalendar(credentials: DavAccount, calendarUrl: String, edit: RemoteCalendarEdit)

    /** Fetch only VEVENT resources overlapping the UTC iCal range [start, end]. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun getEventsInRange(
        credentials: DavAccount,
        calendarUrl: String,
        start: String,
        end: String,
    ): List<RemoteDavEvent>

    /**
     * Cold, pull-based download of VEVENT resources overlapping [start, end] (UTC iCalendar values).
     *
     * Each emitted list is bounded to 32 events. A new batch is not requested from Rust until the
     * downstream collector resumes. A cancelled collector closes the underlying HTTP response;
     * transport errors propagate rather than completing the flow successfully. The raw ICS and
     * detached overrides are retained exactly as with [getEventsInRange].
     */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    fun streamEventsInRange(
        credentials: DavAccount,
        calendarUrl: String,
        start: String,
        end: String,
    ): Flow<List<RemoteDavEvent>>

    /** Fetch only hrefs and etags for VEVENT resources overlapping [start, end]. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun getEventRefsInRange(
        credentials: DavAccount,
        calendarUrl: String,
        start: String,
        end: String,
    ): List<RemoteDavEventRef>

    /** Sync a calendar incrementally. [syncToken] can be null for a first full sync pass. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun syncCollection(
        credentials: DavAccount,
        calendarUrl: String,
        syncToken: String?,
    ): RemoteEventSyncDelta

    /** Fetch a specific list of event URLs (href) using CalDAV calendar-multiget. */
    @Throws(CancellationException::class, CaldavBridgeException::class)
    suspend fun getEventsByUrls(
        credentials: DavAccount,
        calendarUrl: String,
        eventUrls: List<String>,
    ): List<RemoteDavEvent>

    /**
     * Apply [edit] onto an existing iCS, returning the patched event reparsed from its final
     * serialization (bumped SEQUENCE, refreshed DTSTAMP/LAST-MODIFIED, …). Its [RemoteDavEvent.icsData]
     * holds the ICS to send to the server; `url`/`etag` are empty until the server assigns them.
     * No network.
     */
    suspend fun patchEventIcs(icsData: String, edit: RemoteEventEdit): RemoteDavEvent

    /**
     * Build an iCS (one VEVENT, new UID) from [edit], returning it reparsed (see [patchEventIcs]).
     *
     * [seed] makes the VEVENT start from an existing one rather than from an empty one, so a series
     * tail keeps the organizer, attendees, status and custom properties [RemoteEventEdit] cannot
     * represent. No network.
     */
    suspend fun buildEventIcs(edit: RemoteEventEdit, seed: RemoteVeventSeed? = null): RemoteDavEvent

    /**
     * Add — or replace — the VEVENT overriding the instance [recurrenceId] designates, inside the same
     * iCS as its master (RFC 5545 §3.8.4.4). Returns the whole resource reparsed (see [patchEventIcs]):
     * master, and every override including this one.
     *
     * A newly detached instance is seeded from its master; [seed] makes it clone that VEVENT instead,
     * which is how an override carried onto another series keeps its own content. No network.
     */
    suspend fun upsertOverrideIcs(
        icsData: String,
        recurrenceId: RemoteRecurrenceId,
        edit: RemoteEventEdit,
        seed: RemoteVeventSeed? = null,
    ): RemoteDavEvent

    /**
     * Set to [partStat] the `PARTSTAT` of the `ATTENDEE` matching one of [emails] in the VEVENTs [scope]
     * designates, leaving `SEQUENCE` to the organizer. Returns the whole resource reparsed (see
     * [patchEventIcs]); fails when none of those VEVENTs lists the user. No network.
     */
    suspend fun setParticipationStatus(
        icsData: String,
        scope: RemoteReplyScope,
        emails: List<String>,
        partStat: String,
        stamp: String,
    ): RemoteDavEvent

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
