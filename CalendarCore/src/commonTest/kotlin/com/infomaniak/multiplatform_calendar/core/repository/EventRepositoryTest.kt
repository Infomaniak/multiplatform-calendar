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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.repository.EventRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteCalendarEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteSyncCollectionResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventRepositoryTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var repository: EventRepository

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(
            driver = DatabaseProviderFactory.driver(),
            inMemory = true,
        )
        repository = EventRepository(
            accountDao = database.accountDao(),
            caldavClient = NoOpCaldavClient,
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
            dtStart = dtStart,
            dtEndEffective = dtEnd,
            startTimeZone = null,
            endTimeZone = null,
            dtStartInstantMs = null,
            dtEndInstantMs = null,
            etag = "1",
            rawIcs = "",
        )
    }

    private fun eventDao() = database.eventDao()
}

/** Stub — [EventRepositoryTest] only exercises the read path, no remote call is made. */
private object NoOpCaldavClient : CalendarSyncRemoteSource {
    override suspend fun discoverCalendars(credentials: DavAccount) = emptyList<RemoteDavCalendar>()
    override suspend fun updateCalendar(credentials: DavAccount, calendarUrl: String, edit: RemoteCalendarEdit) = Unit
    override suspend fun getEvents(credentials: DavAccount, calendarUrl: String) = emptyList<RemoteDavEvent>()
    override suspend fun getEventsInRange(credentials: DavAccount, calendarUrl: String, start: String, end: String) =
        emptyList<RemoteDavEvent>()

    override suspend fun syncCollection(credentials: DavAccount, calendarUrl: String, syncToken: String?) =
        RemoteSyncCollectionResult(syncToken = syncToken, items = emptyList())

    override suspend fun getEventsByUrls(credentials: DavAccount, calendarUrl: String, eventUrls: List<String>) =
        emptyList<RemoteDavEvent>()

    override suspend fun patchEventIcs(icsData: String, edit: RemoteEventEdit) = icsData
    override suspend fun buildEventIcs(edit: RemoteEventEdit) = ""
    override suspend fun createEvent(credentials: DavAccount, calendarUrl: String, icsData: String) =
        RemoteDavEventRef(url = "", etag = "")

    override suspend fun updateEvent(credentials: DavAccount, eventUrl: String, etag: String, icsData: String) =
        RemoteDavEventRef(url = "", etag = "")

    override suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String) = Unit
}
