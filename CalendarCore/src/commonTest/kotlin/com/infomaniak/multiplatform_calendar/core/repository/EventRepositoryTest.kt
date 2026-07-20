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
package com.infomaniak.multiplatform_calendar.core.repository

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.repository.EventRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeRole
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ParticipationStatus
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteCalendarEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavAttendee
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventSyncDelta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventRepositoryTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var repository: EventRepository
    private lateinit var fakeCaldav: FakeCaldavClient

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(
            driver = DatabaseProviderFactory.driver(),
            inMemory = true,
        )
        fakeCaldav = FakeCaldavClient()
        repository = EventRepository(
            accountDao = database.accountDao(),
            caldavClient = fakeCaldav,
            eventDao = database.eventDao(),
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    /**
     * Regression: `observeVisibleEvents(zone = X)` must feed the wall-clock range bounds in [X]
     * to the DAO so floating events are filtered against the caller-provided zone, not the device
     * zone. Otherwise `observeVisibleDaySlices(timeZone = X)` (which forwards the same [X]) would
     * filter events in one zone and slice them in another, producing inconsistent results.
     */
    @Test
    fun observeVisibleEvents_usesProvidedZoneForFloatingBounds() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        // Floating event: wall-clock 10:00 → 11:00, no zone (dtStartInstantMs = null).
        eventDao().upsert(listOf(floatingEvent(calendarId)))

        // Same absolute Instant range, but interpreted in different zones for the SQL wall-clock bounds:
        //  - UTC:                    10:00-10:30 wall → OVERLAPS the 10:00-11:00 floating event
        //  - Paris (UTC+2 in summer):12:00-12:30 wall → does NOT overlap (starts after event ends)
        val rangeStart = LocalDateTime(2026, 6, 15, 10, 0).toInstant(TimeZone.UTC)
        val rangeEnd = LocalDateTime(2026, 6, 15, 10, 30).toInstant(TimeZone.UTC)

        val utcResult = repository.observeVisibleEvents(
            accountIds = setOf(account),
            start = rangeStart,
            end = rangeEnd,
            zone = TimeZone.UTC,
        ).first()
        val parisResult = repository.observeVisibleEvents(
            accountIds = setOf(account),
            start = rangeStart,
            end = rangeEnd,
            zone = TimeZone.of("Europe/Paris"),
        ).first()

        assertEquals(1, utcResult.size, "UTC bounds (10:00-10:30 wall) should overlap 10:00-11:00")
        assertEquals(0, parisResult.size, "Paris bounds (12:00-12:30 wall) should not overlap 10:00-11:00")
    }

    /**
     * Regression: a cross-calendar move persists the patched event **reparsed from its final ICS**,
     * so every server-only field (attendees, categories, rrule, status, sequence, refreshed
     * revision metadata, …) lands in the new local row, rebound to the new href/calendar/etag.
     */
    @Test
    fun updateEvent_crossCalendarMove_preservesServerOnlyFields_andRebindsRow() = runTest {
        val account = AccountId(1)
        val source = CalendarId("calendar://source")
        val target = CalendarId("calendar://target")
        seedCalendar(account, source)
        seedCalendar(account, target)

        val oldId = EventId("https://cal/source/event.ics")
        eventDao().upsert(listOf(richEvent(id = oldId, calendarId = source, etag = "etag-old")))

        // The bridge reparses the patched ICS; the fake returns that parsed event directly.
        fakeCaldav.patchedEvent = remoteDavEvent(
            icsData = "BEGIN:VEVENT\nUID:1\nSUMMARY:Renamed\nEND:VEVENT",
            summary = "Renamed",
            created = "20260101T080000Z",
            lastModified = "20260615T090000Z",
            dtstamp = "20260615T090000Z",
            rrule = "FREQ=WEEKLY;BYDAY=MO",
            status = "CONFIRMED",
            transp = "OPAQUE",
            classification = "PRIVATE",
            priority = "5",
            sequence = "4",
            categories = "work,urgent",
            attendees = listOf(
                RemoteDavAttendee(
                    email = "guest@example.com",
                    displayName = null,
                    status = "ACCEPTED",
                    role = "REQ-PARTICIPANT",
                    isOrganizer = false,
                    responseNeeded = false,
                ),
            ),
        )
        fakeCaldav.createdRef = RemoteDavEventRef(url = "https://cal/target/event.ics", etag = "etag-new")

        repository.updateEvent(
            credentials = DavAccount(baseUrl = "https://cal/", username = "u", password = "p"),
            eventId = oldId,
            data = editData(title = "Renamed", calendarId = target),
        )

        // Remote choreography: create on the target (with the patched ICS), then delete the source.
        assertEquals(listOf("calendar://target" to fakeCaldav.patchedEvent.icsData), fakeCaldav.creates)
        assertEquals(listOf(oldId.url to "etag-old"), fakeCaldav.deletes)

        // Old local row is gone; new row lives at the new href in the target calendar.
        assertNull(database.eventDao().getEvent(oldId))
        val moved = database.eventDao().getEvent(EventId("https://cal/target/event.ics"))!!
        assertEquals(target, moved.calendarId)
        assertEquals("etag-new", moved.etag)
        assertEquals(fakeCaldav.patchedEvent.icsData, moved.rawIcs)
        assertEquals("Renamed", moved.summary)
        assertTrue(moved.isSynced)

        // Every parsed field from the patched event is persisted.
        assertEquals("FREQ=WEEKLY;BYDAY=MO", moved.rrule)
        assertEquals(EventStatus.CONFIRMED, moved.status)
        assertEquals("OPAQUE", moved.transp)
        assertEquals(Classification.Private, moved.classification)
        assertEquals(5, moved.priority)
        assertEquals(listOf("work", "urgent"), moved.categories)
        assertEquals("guest@example.com", moved.attendees.single().email)

        // Revision metadata comes straight from the reparsed ICS (bumped by Rust's bump_revision).
        assertEquals(4, moved.sequence)
        assertEquals(LocalDateTime(2026, 6, 15, 9, 0), moved.dtStamp)
        assertEquals(LocalDateTime(2026, 6, 15, 9, 0), moved.lastModified)
        assertEquals(LocalDateTime(2026, 1, 1, 8, 0), moved.created)
    }

    /**
     * The CSS3 `COLOR` name survives a move because the local row is rebuilt from the patched ICS:
     * whatever color the ICS carries (here an unchanged `COLOR:royalblue`) round-trips into the row.
     */
    @Test
    fun updateEvent_crossCalendarMove_persistsIcalColorNameFromPatchedIcs() = runTest {
        val account = AccountId(1)
        val source = CalendarId("calendar://source")
        val target = CalendarId("calendar://target")
        seedCalendar(account, source)
        seedCalendar(account, target)

        val oldId = EventId("https://cal/source/event.ics")
        eventDao().upsert(listOf(richEvent(id = oldId, calendarId = source, etag = "etag-old")))

        fakeCaldav.patchedEvent = remoteDavEvent(
            icsData = "BEGIN:VEVENT\nUID:1\nCOLOR:royalblue\nEND:VEVENT",
            colorIcalName = "royalblue",
        )
        fakeCaldav.createdRef = RemoteDavEventRef(url = "https://cal/target/event.ics", etag = "etag-new")

        repository.updateEvent(
            credentials = DavAccount(baseUrl = "https://cal/", username = "u", password = "p"),
            eventId = oldId,
            data = editData(title = "Original", calendarId = target),
        )

        val moved = database.eventDao().getEvent(EventId("https://cal/target/event.ics"))!!
        assertEquals("royalblue", moved.colorIcalName)
    }

    /**
     * A same-calendar update persists the patched event reparsed from its ICS, so the refreshed
     * revision metadata (SEQUENCE bumped, DTSTAMP/LAST-MODIFIED rewritten by Rust) lands verbatim.
     */
    @Test
    fun updateEvent_sameCalendar_persistsRefreshedRevisionFromPatchedIcs() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        val eventId = EventId("https://cal/main/event.ics")
        eventDao().upsert(listOf(richEvent(id = eventId, calendarId = calendarId, etag = "etag-old")))

        fakeCaldav.patchedEvent = remoteDavEvent(
            icsData = "BEGIN:VEVENT\nUID:1\nSUMMARY:Renamed\nEND:VEVENT",
            summary = "Renamed",
            sequence = "4",
            dtstamp = "20260615T090000Z",
            lastModified = "20260615T090000Z",
        )

        repository.updateEvent(
            credentials = DavAccount(baseUrl = "https://cal/", username = "u", password = "p"),
            eventId = eventId,
            data = editData(title = "Renamed", calendarId = calendarId),
        )

        val updated = database.eventDao().getEvent(eventId)!!
        assertEquals("Renamed", updated.summary)
        assertEquals(fakeCaldav.patchedEvent.icsData, updated.rawIcs)
        assertEquals(4, updated.sequence)
        assertEquals(LocalDateTime(2026, 6, 15, 9, 0), updated.dtStamp)
        assertEquals(LocalDateTime(2026, 6, 15, 9, 0), updated.lastModified)
    }

    /** createEvent persists the built event reparsed from its ICS, bound to the server ref. */
    @Test
    fun createEvent_persistsBuiltEventBoundToServerRef() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        fakeCaldav.patchedEvent = remoteDavEvent(
            icsData = "BEGIN:VEVENT\nUID:new\nSUMMARY:Fresh\nEND:VEVENT",
            summary = "Fresh",
            sequence = "0",
            dtstamp = "20260615T090000Z",
        )
        fakeCaldav.createdRef = RemoteDavEventRef(url = "https://cal/main/new.ics", etag = "etag-1")

        repository.createEvent(
            credentials = DavAccount(baseUrl = "https://cal/", username = "u", password = "p"),
            data = editData(title = "Fresh", calendarId = calendarId),
        )

        assertEquals(listOf("calendar://main" to fakeCaldav.patchedEvent.icsData), fakeCaldav.creates)
        val created = database.eventDao().getEvent(EventId("https://cal/main/new.ics"))!!
        assertEquals(calendarId, created.calendarId)
        assertEquals("etag-1", created.etag)
        assertEquals("Fresh", created.summary)
        assertEquals(0, created.sequence)
        assertTrue(created.isSynced)
    }

    private fun richEvent(id: EventId, calendarId: CalendarId, etag: String): EventEntity = EventEntity(
        id = id,
        calendarId = calendarId,
        summary = "Original",
        timing = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
            dtStartInstantMs = LocalDateTime(2026, 6, 15, 10, 0).toInstant(TimeZone.UTC).toEpochMilliseconds(),
            dtEndInstantMs = LocalDateTime(2026, 6, 15, 11, 0).toInstant(TimeZone.UTC).toEpochMilliseconds(),
        ),
        created = LocalDateTime(2026, 1, 1, 8, 0),
        lastModified = LocalDateTime(2026, 1, 2, 9, 0),
        dtStamp = LocalDateTime(2026, 1, 2, 9, 0),
        rrule = "FREQ=WEEKLY;BYDAY=MO",
        status = EventStatus.CONFIRMED,
        transp = "OPAQUE",
        classification = Classification.Private,
        priority = 5,
        sequence = 3,
        categories = listOf("work", "urgent"),
        attendees = listOf(
            AttendeeEntity(
                email = "guest@example.com",
                status = ParticipationStatus.Accepted,
                role = AttendeeRole.Requested,
            ),
        ),
        etag = etag,
        rawIcs = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
    )

    private suspend fun seedCalendar(accountId: AccountId, calendarId: CalendarId) {
        database.accountDao().insert(AccountEntity(id = accountId))
        database.calendarDao().upsert(
            listOf(
                CalendarEntity(
                    id = calendarId,
                    accountId = accountId,
                    displayName = "cal",
                    color = null,
                    isVisible = true,
                ),
            ),
        )
    }

    private fun floatingEvent(calendarId: CalendarId): EventEntity {
        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        val dtEnd = LocalDateTime(2026, 6, 15, 11, 0)
        return EventEntity(
            id = EventId("event://floating"),
            calendarId = calendarId,
            summary = "Floating 10-11",
            timing = EventTimingEntity(
                dtStart = dtStart,
                dtEndEffective = dtEnd,
                startTimeZone = null,
                endTimeZone = null,
                dtStartInstantMs = null,
                dtEndInstantMs = null,
            ),
            etag = "1",
            rawIcs = "",
        )
    }

    private fun editData(title: String, calendarId: CalendarId) = EventEditData(
        title = title,
        timing = EventTiming(
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 11, 0),
            startTimeZone = TimeZone.UTC,
            endTimeZone = TimeZone.UTC,
            isAllDay = false,
        ),
        location = null,
        description = null,
        calendarId = calendarId,
        eventColor = null,
        alarms = emptyList(),
    )

    private fun eventDao() = database.eventDao()
}

private fun remoteDavEvent(
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
    rrule = rrule,
    status = status,
    transp = transp,
    classification = classification,
    priority = priority,
    sequence = sequence,
    categories = categories,
    colorHex = colorHex,
    colorIcalName = colorIcalName,
    attendees = attendees,
)

/** Fake remote source: records create/delete calls and returns [patchedEvent] for patch/build. */
private class FakeCaldavClient : CalendarSyncRemoteSource {
    var patchedEvent: RemoteDavEvent = remoteDavEvent(icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT")
    var createdRef: RemoteDavEventRef = RemoteDavEventRef(url = "", etag = "")
    val creates = mutableListOf<Pair<String, String>>()
    val deletes = mutableListOf<Pair<String, String>>()

    override suspend fun discoverCalendars(credentials: DavAccount) = emptyList<RemoteDavCalendar>()
    override suspend fun updateCalendar(credentials: DavAccount, calendarUrl: String, edit: RemoteCalendarEdit) = Unit
    override suspend fun getEvents(credentials: DavAccount, calendarUrl: String) = emptyList<RemoteDavEvent>()
    override suspend fun getEventsInRange(credentials: DavAccount, calendarUrl: String, start: String, end: String) =
        emptyList<RemoteDavEvent>()

    override suspend fun getEventRefsInRange(credentials: DavAccount, calendarUrl: String, start: String, end: String) =
        emptyList<RemoteDavEventRef>()

    override suspend fun syncCollection(credentials: DavAccount, calendarUrl: String, syncToken: String?) =
        RemoteEventSyncDelta(syncToken = syncToken, items = emptyList())

    override suspend fun getEventsByUrls(credentials: DavAccount, calendarUrl: String, eventUrls: List<String>) =
        emptyList<RemoteDavEvent>()

    override suspend fun patchEventIcs(icsData: String, edit: RemoteEventEdit) = patchedEvent
    override suspend fun buildEventIcs(edit: RemoteEventEdit) = patchedEvent
    override suspend fun createEvent(credentials: DavAccount, calendarUrl: String, icsData: String): RemoteDavEventRef {
        creates += calendarUrl to icsData
        return createdRef
    }

    override suspend fun updateEvent(credentials: DavAccount, eventUrl: String, etag: String, icsData: String) =
        RemoteDavEventRef(url = eventUrl, etag = etag)

    override suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String) {
        deletes += eventUrl to etag
    }
}
