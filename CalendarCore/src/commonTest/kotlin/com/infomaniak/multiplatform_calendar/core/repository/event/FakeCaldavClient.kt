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
package com.infomaniak.multiplatform_calendar.core.repository.event

import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAlarmEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteCalendarEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavAlarm
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavAttendee
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavDiscovery
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventContent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventSyncDelta
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteVeventSeed

/** Fake remote source: records create/delete calls and returns [patchedEvent] for patch/build,
 *  optionally folding the [RemoteEventEdit] into it via [applyEdit] to emulate the Rust bridge. */
internal class FakeCaldavClient : CalendarSyncRemoteSource {
    var patchedEvent: RemoteDavEvent = remoteDavEvent(icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT")
    var createdRef: RemoteDavEventRef = RemoteDavEventRef(url = "", etag = "")
    /**
     * When set, emulates the real Rust bridge: it rewrites the ICS from the edit and reparses it, so the
     * returned event reflects the edited recurrence/alarms. Null (default) returns [patchedEvent] verbatim,
     * matching the "bridge already reparsed this" fixtures the other tests rely on.
     */
    var applyEdit: ((base: RemoteDavEvent, edit: RemoteEventEdit) -> RemoteDavEvent)? = null
    val creates = mutableListOf<Pair<String, String>>()
    val deletes = mutableListOf<Pair<String, String>>()
    val patches = mutableListOf<RemoteEventEdit>()
    val builds = mutableListOf<RemoteEventEdit>()
    /** What each build/override was seeded from, so the tests can assert the source ICS reaches the bridge. */
    val buildSeeds = mutableListOf<RemoteVeventSeed?>()
    val overrideSeeds = mutableListOf<RemoteVeventSeed?>()
    val overrideUpserts = mutableListOf<Pair<RemoteRecurrenceId, RemoteEventEdit>>()
    /** Every write in the order it was issued, for the tests that care about which one lands first. */
    val calls = mutableListOf<String>()

    override suspend fun discover(credentials: DavAccount) = RemoteDavDiscovery(emptyList(), emptyList())
    override suspend fun updateCalendar(credentials: DavAccount, calendarUrl: String, edit: RemoteCalendarEdit) = Unit
    override suspend fun getEventsInRange(credentials: DavAccount, calendarUrl: String, start: String, end: String) =
        emptyList<RemoteDavEvent>()

    override suspend fun getEventRefsInRange(credentials: DavAccount, calendarUrl: String, start: String, end: String) =
        emptyList<RemoteDavEventRef>()

    override suspend fun syncCollection(credentials: DavAccount, calendarUrl: String, syncToken: String?) =
        RemoteEventSyncDelta(syncToken = syncToken, items = emptyList())

    override suspend fun getEventsByUrls(credentials: DavAccount, calendarUrl: String, eventUrls: List<String>) =
        emptyList<RemoteDavEvent>()

    override suspend fun patchEventIcs(icsData: String, edit: RemoteEventEdit): RemoteDavEvent {
        patches += edit
        calls += "patch"
        return applyEdit?.invoke(patchedEvent, edit) ?: patchedEvent
    }

    override suspend fun buildEventIcs(edit: RemoteEventEdit, seed: RemoteVeventSeed?): RemoteDavEvent {
        builds += edit
        buildSeeds += seed
        calls += "build"
        return applyEdit?.invoke(patchedEvent, edit) ?: patchedEvent
    }

    override suspend fun upsertOverrideIcs(
        icsData: String,
        recurrenceId: RemoteRecurrenceId,
        edit: RemoteEventEdit,
        seed: RemoteVeventSeed?,
    ): RemoteDavEvent {
        overrideUpserts += recurrenceId to edit
        overrideSeeds += seed
        calls += "override"
        return applyEdit?.invoke(patchedEvent, edit) ?: patchedEvent
    }

    override suspend fun createEvent(credentials: DavAccount, calendarUrl: String, icsData: String): RemoteDavEventRef {
        creates += calendarUrl to icsData
        calls += "create"
        return createdRef
    }

    override suspend fun updateEvent(credentials: DavAccount, eventUrl: String, etag: String, icsData: String) =
        RemoteDavEventRef(url = eventUrl, etag = etag)

    override suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String) {
        deletes += eventUrl to etag
    }
}

internal fun remoteDavEvent(
    icsData: String,
    summary: String? = "Event",
    dtstart: String? = "20260615T100000Z",
    dtend: String? = "20260615T110000Z",
    created: String? = null,
    lastModified: String? = null,
    dtstamp: String? = null,
    rrule: String? = null,
    status: String? = null,
    transp: String? = null,
    classification: String? = null,
    priority: String? = null,
    sequence: String? = null,
    categories: String? = null,
    colorHex: String? = null,
    colorIcalName: String? = null,
    attendees: List<RemoteDavAttendee> = emptyList(),
) = RemoteDavEvent(
    url = "",
    etag = "",
    icsData = icsData,
    uid = "uid",
    rrule = rrule,
    content = RemoteDavEventContent(
        summary = summary,
        description = null,
        location = null,
        dtstart = dtstart,
        dtStartTzid = null,
        dtend = dtend,
        dtEndTzid = null,
        duration = null,
        created = created,
        lastModified = lastModified,
        dtstamp = dtstamp,
        status = status,
        transp = transp,
        classification = classification,
        priority = priority,
        sequence = sequence,
        categories = categories,
        colorHex = colorHex,
        colorIcalName = colorIcalName,
        attendees = attendees,
    ),
)

/**
 * Emulates the Rust bridge's edit round-trip: it rewrites the VEVENT from [edit] and reparses it, so the
 * returned event mirrors the edited recurrence rule and alarms. Mirrors the bridge contract narrowly (the
 * only fields exercised here): RRULE set/cleared/untouched and the VALARM list replace-or-keep semantics.
 */
internal fun bridgeApplyingEdit(base: RemoteDavEvent, edit: RemoteEventEdit): RemoteDavEvent = base.copy(
    rrule = when (val change = edit.recurrenceChange) {
        is RemoteRecurrenceChange.Set -> change.value
        RemoteRecurrenceChange.Cleared -> null
        RemoteRecurrenceChange.Unchanged -> base.rrule
    },
    content = base.content.copy(alarms = edit.alarms?.map { it.toRemoteDavAlarm() } ?: base.content.alarms),
)

private fun RemoteAlarmEdit.toRemoteDavAlarm() = RemoteDavAlarm(
    uid = uid,
    action = action,
    triggerDuration = triggerDuration,
    triggerAbsolute = triggerAbsolute,
    triggerRelatedTo = triggerRelatedTo,
    description = description,
    summary = summary,
    attendees = attendees,
    attach = attach,
    repetition = repetition,
)
