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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteCalendarEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavAttendee
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteSyncCollectionItem
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteSyncCollectionResult
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteVTimeZone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import uniffi.caldav_bridge.CaldavException
import uniffi.caldav_bridge.CalendarEdit
import uniffi.caldav_bridge.EventEdit
import uniffi.caldav_bridge.EventEntry
import uniffi.caldav_bridge.VTimeZoneSpec
import uniffi.caldav_bridge.discover
import uniffi.caldav_bridge.fetchEvents
import uniffi.caldav_bridge.DavAccount as RustDavAccount
import uniffi.caldav_bridge.buildEventIcs as rustBuildEventIcs
import uniffi.caldav_bridge.calendarMultiget as rustCalendarMultiget
import uniffi.caldav_bridge.calendarQueryTimerange as rustCalendarQueryTimerange
import uniffi.caldav_bridge.createEvent as rustCreateEvent
import uniffi.caldav_bridge.deleteEvent as rustDeleteEvent
import uniffi.caldav_bridge.patchEventIcs as rustPatchEventIcs
import uniffi.caldav_bridge.syncCollection as rustSyncCollection
import uniffi.caldav_bridge.updateCalendar as rustUpdateCalendar
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
            val entries = discover(credentials.toRust())
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

    override suspend fun updateCalendar(
        credentials: DavAccount,
        calendarUrl: String,
        edit: RemoteCalendarEdit,
    ) = withContext(dispatcher) {
        if (!edit.hasChanges) return@withContext
        try {
            rustUpdateCalendar(
                credentials.toRust(),
                calendarUrl,
                CalendarEdit(displayName = edit.displayName, color = edit.color),
            )
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("updateCalendar")
        }
    }

    override suspend fun getEvents(
        credentials: DavAccount,
        calendarUrl: String,
    ): List<RemoteDavEvent> = withContext(dispatcher) {
        try {
            val entries = fetchEvents(credentials.toRust(), calendarUrl)
            return@withContext entries.map(EventEntry::toRemoteEvent)

        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("getEvents")
        }
    }

    override suspend fun getEventsInRange(
        credentials: DavAccount,
        calendarUrl: String,
        start: String,
        end: String,
    ): List<RemoteDavEvent> = withContext(dispatcher) {
        try {
            val entries = rustCalendarQueryTimerange(credentials.toRust(), calendarUrl, start, end)
            return@withContext entries.map(EventEntry::toRemoteEvent)
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("getEventsInRange")
        }
    }

    override suspend fun syncCollection(
        credentials: DavAccount,
        calendarUrl: String,
        syncToken: String?,
    ): RemoteSyncCollectionResult = withContext(dispatcher) {
        try {
            val result = rustSyncCollection(credentials.toRust(), calendarUrl, syncToken)
            RemoteSyncCollectionResult(
                syncToken = result.syncToken,
                items = result.items.map { item ->
                    RemoteSyncCollectionItem(
                        eventUrl = item.href,
                        isDeleted = item.isDeleted,
                    )
                },
            )
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("syncCollection")
        }
    }

    override suspend fun getEventsByUrls(
        credentials: DavAccount,
        calendarUrl: String,
        eventUrls: List<String>,
    ): List<RemoteDavEvent> = withContext(dispatcher) {
        if (eventUrls.isEmpty()) return@withContext emptyList()
        try {
            val entries = rustCalendarMultiget(credentials.toRust(), calendarUrl, eventUrls)
            return@withContext entries.map(EventEntry::toRemoteEvent)
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("getEventsByUrls")
        }
    }

    override suspend fun createEvent(
        credentials: DavAccount,
        calendarUrl: String,
        icsData: String,
    ): RemoteDavEventRef = withContext(dispatcher) {
        try {
            val result = rustCreateEvent(credentials.toRust(), calendarUrl, icsData)
            return@withContext RemoteDavEventRef(url = result.url, etag = result.etag)
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
            val result = rustUpdateEvent(credentials.toRust(), eventUrl, etag, icsData)
            return@withContext RemoteDavEventRef(url = result.url, etag = result.etag)
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("updateEvent")
        }
    }

    override suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String) =
        withContext(dispatcher) {
            try {
                rustDeleteEvent(credentials.toRust(), eventUrl, etag)
            } catch (e: CaldavException) {
                throw e.toCaldavBridgeException("deleteEvent")
            }
        }

    override suspend fun patchEventIcs(icsData: String, edit: RemoteEventEdit): String = withContext(dispatcher) {
        try {
            rustPatchEventIcs(icsData, edit.toRust())
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("patchEventIcs")
        }
    }

    override suspend fun buildEventIcs(edit: RemoteEventEdit): String = withContext(dispatcher) {
        try {
            rustBuildEventIcs(edit.toRust())
        } catch (e: CaldavException) {
            throw e.toCaldavBridgeException("buildEventIcs")
        }
    }
}

private fun DavAccount.toRust() = RustDavAccount(
    baseUrl = baseUrl,
    username = username,
    password = password,
)

private fun RemoteEventEdit.toRust() = EventEdit(
    summary = summary,
    dtstart = dtStart,
    dtstartTzid = dtStartTzid,
    dtend = dtEnd,
    dtendTzid = dtEndTzid,
    allDay = allDay,
    location = location,
    description = description,
    timezones = timeZones.map { it.toRust() },
    stamp = stamp,
)

private fun RemoteVTimeZone.toRust() = VTimeZoneSpec(tzid = tzid, offset = offset)

private fun EventEntry.toRemoteEvent(): RemoteDavEvent {
    return RemoteDavEvent(
        url = this.url,
        etag = this.etag,
        icsData = this.icsData,
        uid = this.uid,
        summary = this.summary,
        description = this.description,
        location = this.location,
        dtstart = this.dtstart,
        dtStartTzid = this.dtstartTzid,
        dtend = this.dtend,
        dtEndTzid = this.dtendTzid,
        duration = this.duration,
        created = this.created,
        lastModified = this.lastModified,
        dtstamp = this.dtstamp,
        rrule = this.rrule,
        status = this.status,
        transp = this.transp,
        classification = this.classification,
        priority = this.priority,
        sequence = this.sequence,
        categories = this.categories,
        attendees = this.attendees.map { attendee ->
            RemoteDavAttendee(
                email = attendee.email,
                displayName = attendee.displayName,
                status = attendee.status,
                role = attendee.role,
                isOrganizer = attendee.isOrganizer,
                responseNeeded = attendee.responseNeeded,
            )
        },
    )
}

