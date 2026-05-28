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
package com.infomaniak.multiplatform_calendar.data.remote

import com.infomaniak.multiplatform_calendar.core.data.remote.CaldavBridgeException
import com.infomaniak.multiplatform_calendar.core.data.remote.CaldavClient
import com.infomaniak.multiplatform_calendar.core.data.remote.model.CaldavCredentials
import com.infomaniak.multiplatform_calendar.core.data.remote.model.RemoteCalendar
import com.infomaniak.multiplatform_calendar.core.data.remote.model.RemoteEvent
import com.infomaniak.multiplatform_calendar.core.data.remote.model.RemoteEventRef
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseHexColor
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
object RustCaldavBridge : CaldavClient {

    override suspend fun discoverCalendars(credentials: CaldavCredentials): List<RemoteCalendar> {
        try {
            val entries = discover(credentials.baseUrl, credentials.username, credentials.password)
            return entries.map { entry ->
                RemoteCalendar(
                    url = entry.url,
                    displayName = entry.displayName,
                    color = entry.color?.let(::parseHexColor),
                    description = entry.description,
                    ctag = entry.ctag,
                )
            }
        } catch (e: CaldavException) {
            throw CaldavBridgeException(e.message ?: "Unknown error")
        }
    }

    override suspend fun getEvents(credentials: CaldavCredentials, calendarUrl: String): List<RemoteEvent> {
        try {
            val entries = fetchEvents(credentials.baseUrl, credentials.username, credentials.password, calendarUrl)
            return entries.map { entry ->
                RemoteEvent(
                    url = entry.url,
                    etag = entry.etag,
                    icsData = entry.icsData,
                    uid = entry.uid,
                    summary = entry.summary,
                    description = entry.description,
                    location = entry.location,
                    dtstart = entry.dtstart?.let(::parseICalDateTime),
                    dtend = entry.dtend?.let(::parseICalDateTime),
                    rrule = entry.rrule,
                    status = entry.status,
                )
            }
        } catch (e: CaldavException) {
            throw CaldavBridgeException(e.message ?: "Unknown error")
        }
    }

    override suspend fun createEvent(credentials: CaldavCredentials, calendarUrl: String, icsData: String): RemoteEventRef {
        try {
            val result = rustCreateEvent(credentials.baseUrl, credentials.username, credentials.password, calendarUrl, icsData)
            return RemoteEventRef(
                url = result.url,
                etag = result.etag,
            )
        } catch (e: CaldavException) {
            throw CaldavBridgeException(e.message ?: "Unknown error")
        }
    }

    override suspend fun updateEvent(
        credentials: CaldavCredentials,
        eventUrl: String,
        etag: String,
        icsData: String
    ): RemoteEventRef {
        try {
            val result = rustUpdateEvent(credentials.baseUrl, credentials.username, credentials.password, eventUrl, etag, icsData)
            return RemoteEventRef(
                url = result.url,
                etag = result.etag,
            )
        } catch (e: CaldavException) {
            throw CaldavBridgeException(e.message ?: "Unknown error")
        }
    }

    override suspend fun deleteEvent(credentials: CaldavCredentials, eventUrl: String, etag: String) {
        try {
            rustDeleteEvent(credentials.baseUrl, credentials.username, credentials.password, eventUrl, etag)
        } catch (e: CaldavException) {
            throw CaldavBridgeException(e.message ?: "Unknown error")
        }
    }
}
