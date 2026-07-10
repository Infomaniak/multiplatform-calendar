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
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteCalendarEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteSyncCollectionResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarRepositoryTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun syncCalendars_skipsEventsThatFailToParse_andUpsertsTheRest() = runTest {
        val accountId = AccountId(1)
        database.accountDao().insert(AccountEntity(accountId))
        val calendarUrl = "https://dav.example/cal/1/"
        val calendarId = CalendarId(calendarUrl)
        val validBefore = remoteEvent(url = "${calendarUrl}valid-before.ics", uid = "uid-1")
        val invalid = remoteEvent(url = "${calendarUrl}invalid.ics", uid = "uid-2", dtstart = null)
        val validAfter = remoteEvent(url = "${calendarUrl}valid-after.ics", uid = "uid-3")
        val remote = FakeCalendarSyncRemoteSource(
            calendars = listOf(RemoteDavCalendar(url = calendarUrl, displayName = "Cal")),
            events = mapOf(calendarUrl to listOf(validBefore, invalid, validAfter)),
        )
        val repository = CalendarRepository(
            caldavClient = remote,
            calendarDao = database.calendarDao(),
            eventDao = database.eventDao(),
        )

        repository.syncCalendars(accountId, fakeCredentials())

        val storedIds = database.eventDao().observeEvents(calendarId).first().map { it.id.url }
        assertEquals(listOf(validBefore.url, validAfter.url).sorted(), storedIds.sorted())
    }

    @Test
    fun syncCalendars_allEventsInvalid_upsertsEmptyList_doesNotThrow() = runTest {
        val accountId = AccountId(2)
        database.accountDao().insert(AccountEntity(accountId))
        val calendarUrl = "https://dav.example/cal/only-bad/"
        val calendarId = CalendarId(calendarUrl)
        val remote = FakeCalendarSyncRemoteSource(
            calendars = listOf(RemoteDavCalendar(url = calendarUrl, displayName = "Cal")),
            events = mapOf(calendarUrl to listOf(remoteEvent(url = "${calendarUrl}bad.ics", uid = "u", dtstart = null))),
        )
        val repository = CalendarRepository(
            caldavClient = remote,
            calendarDao = database.calendarDao(),
            eventDao = database.eventDao(),
        )

        repository.syncCalendars(accountId, fakeCredentials())

        assertEquals(emptyList(), database.eventDao().observeEvents(calendarId).first())
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun fakeCredentials() = DavAccount(baseUrl = "https://dav.example", username = "u", password = "p")

    private fun remoteEvent(
        url: String,
        uid: String,
        dtstart: String? = "20260615T140000Z",
    ) = RemoteDavEvent(
        url = url,
        etag = "etag",
        icsData = "BEGIN:VEVENT\nUID:$uid\nEND:VEVENT",
        uid = uid,
        summary = "S",
        description = null,
        location = null,
        dtstart = dtstart,
        dtStartTzid = null,
        dtend = null,
        dtEndTzid = null,
        duration = null,
        created = null,
        lastModified = null,
        dtstamp = null,
        rrule = null,
        status = null,
        transp = null,
        classification = null,
        priority = null,
        sequence = null,
        categories = null,
        attendees = emptyList(),
    )

    private class FakeCalendarSyncRemoteSource(
        private val calendars: List<RemoteDavCalendar>,
        private val events: Map<String, List<RemoteDavEvent>>,
    ) : CalendarSyncRemoteSource {
        override suspend fun discoverCalendars(credentials: DavAccount) = calendars
        override suspend fun getEvents(credentials: DavAccount, calendarUrl: String) = events[calendarUrl].orEmpty()
        override suspend fun getEventsInRange(credentials: DavAccount, calendarUrl: String, start: String, end: String) =
            emptyList<RemoteDavEvent>()

        override suspend fun syncCollection(credentials: DavAccount, calendarUrl: String, syncToken: String?) =
            RemoteSyncCollectionResult(syncToken = syncToken, items = emptyList())

        override suspend fun getEventsByUrls(credentials: DavAccount, calendarUrl: String, eventUrls: List<String>) =
            emptyList<RemoteDavEvent>()

        override suspend fun updateCalendar(credentials: DavAccount, calendarUrl: String, edit: RemoteCalendarEdit) = Unit
        override suspend fun patchEventIcs(icsData: String, edit: RemoteEventEdit): String = icsData
        override suspend fun buildEventIcs(edit: RemoteEventEdit): String = ""
        override suspend fun createEvent(credentials: DavAccount, calendarUrl: String, icsData: String) =
            error("not used")

        override suspend fun updateEvent(credentials: DavAccount, eventUrl: String, etag: String, icsData: String) =
            error("not used")

        override suspend fun deleteEvent(credentials: DavAccount, eventUrl: String, etag: String) = Unit
    }
}
