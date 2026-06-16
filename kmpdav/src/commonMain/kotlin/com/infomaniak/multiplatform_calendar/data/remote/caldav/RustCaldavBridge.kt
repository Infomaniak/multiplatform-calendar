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
package com.infomaniak.multiplatform_calendar.data.remote.caldav

import com.infomaniak.multiplatform_calendar.data.remote.caldav.CaldavBridgeException.Companion.toCaldavBridgeException
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import uniffi.caldav_bridge.CaldavException
import uniffi.caldav_bridge.discover
import uniffi.caldav_bridge.fetchEvents
import uniffi.caldav_bridge.createEvent as rustCreateEvent
import uniffi.caldav_bridge.deleteEvent as rustDeleteEvent
import uniffi.caldav_bridge.updateEvent as rustUpdateEvent

/**
 * Typed CalDAV client backed by Rust — lives entirely in commonMain.
 *
 * Uses UniFFI-generated bindings to call Rust functions directly.
 * Returns typed records (data classes) — no opaque handles, no manual FFI.
 */
internal class RustCaldavBridge(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CalendarSyncRemoteSource {

    override suspend fun discoverCalendars(credentials: DavAccount): List<RemoteDavCalendar> = withContext(dispatcher) {
        try {
            val entries = discover(credentials.baseUrl, credentials.username, credentials.password)
            return@withContext entries.map { entry ->
                RemoteDavCalendar(
                    url = entry.url,
                    displayName = entry.displayName,
                    color = entry.color,
                    description = entry.description,
                    ctag = entry.ctag,
                    accessLevel = entry.accessLevel,
                )
            }
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("discoverCalendars")
        }
    }

    override suspend fun getEvents(credentials: DavAccount, calendarUrl: String): List<RemoteDavEvent> = withContext(dispatcher) {
        try {
            val entries = fetchEvents(credentials.baseUrl, credentials.username, credentials.password, calendarUrl)
            return@withContext entries.map { entry ->
                RemoteDavEvent(
                    url = entry.url,
                    etag = entry.etag,
                    icsData = entry.icsData,
                    uid = entry.uid,
                    summary = entry.summary,
                    description = entry.description,
                    location = entry.location,
                    dtstart = entry.dtstart,
                    dtend = entry.dtend,
                    duration = entry.duration,
                    created = entry.created,
                    lastModified = entry.lastModified,
                    dtstamp = entry.dtstamp,
                    rrule = entry.rrule,
                    status = entry.status,
                    transp = entry.transp,
                    classification = entry.classification,
                    priority = entry.priority,
                    sequence = entry.sequence,
                    categories = entry.categories,
                    organizer = entry.organizer,
                )
            }
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("getEvents")
        }
    }

    override suspend fun createEvent(
        credentials: DavAccount,
        calendarUrl: String,
        icsData: String,
    ): RemoteDavEventRef = withContext(dispatcher) {
        try {
            val result = rustCreateEvent(credentials.baseUrl, credentials.username, credentials.password, calendarUrl, icsData)
            return@withContext RemoteDavEventRef(
                url = result.url,
                etag = result.etag,
            )
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("createEvent")
        }
    }

    override suspend fun updateEvent(
        credentials: DavAccount,
        eventUrl: String,
        etag: String,
        icsData: String,
    ): RemoteDavEventRef = withContext(dispatcher) {
        try {
            val result = rustUpdateEvent(credentials.baseUrl, credentials.username, credentials.password, eventUrl, etag, icsData)
            return@withContext RemoteDavEventRef(
                url = result.url,
                etag = result.etag,
            )
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("updateEvent")
        }
    }

    override suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String) = withContext(dispatcher) {
        try {
            rustDeleteEvent(credentials.baseUrl, credentials.username, credentials.password, eventUrl, etag)
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("deleteEvent")
        }
    }
}
