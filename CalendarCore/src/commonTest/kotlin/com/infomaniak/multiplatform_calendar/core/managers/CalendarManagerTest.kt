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
package com.infomaniak.multiplatform_calendar.core.managers

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.remote.AuthDataSource
import com.infomaniak.multiplatform_calendar.core.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.core.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.core.data.repository.EventRepository
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_calendar.core.repository.event.FakeBookableResourceLookup
import com.infomaniak.multiplatform_calendar.core.repository.event.FakeCaldavClient
import com.infomaniak.multiplatform_calendar.core.repository.event.FakeContactsLookup
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavDiscovery
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventSyncDelta
import com.infomaniak.multiplatform_calendar.meet.MeetManager
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.appenvironment.AppEnvironment
import com.infomaniak.multiplatform_core.crashreport.NoOpCrashReport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarManagerTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase

    @BeforeTest
    fun setUp() {
        database = DatabaseProviderFactory.createTestDatabaseConfig()
            .getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun syncEvents_whenCalledConcurrently_runsOnlyOneSync() = runTest {
        val accountId = AccountId(42)
        val calendarUrl = "https://dav.example/calendars/team/"
        val remote = BlockingCalendarSyncRemoteSource(calendarUrl = calendarUrl)
        val accountRepository = createAccountRepository()
        accountRepository.storeCredentials(
            accountId = accountId,
            credentials = DavAccount(baseUrl = "https://dav.example", username = "user", password = "secret"),
        )
        val readyManager = createManager(remote, accountRepository)

        val firstSync = async { readyManager.syncEvents() }
        remote.firstSyncStarted.await()

        val secondSync = async { readyManager.syncEvents() }
        secondSync.await()

        assertEquals(1, remote.syncCollectionCalls)

        remote.releaseFirstSync.complete(Unit)
        firstSync.cancelAndJoin()

        assertEquals(1, remote.syncCollectionCalls)
    }

    private fun createManager(
        remote: CalendarSyncRemoteSource,
        accountRepository: AccountRepository = createAccountRepository(),
    ): CalendarManager {
        val calendarRepository = CalendarRepository(
            caldavClient = remote,
            accountDao = database.accountDao(),
            calendarDao = database.calendarDao(),
            crashReport = NoOpCrashReport,
            eventDao = database.eventDao(),
        )
        val eventRepository = EventRepository(
            accountDao = database.accountDao(),
            caldavClient = remote,
            calendarDao = database.calendarDao(),
            eventDao = database.eventDao(),
            crashReport = NoOpCrashReport,
            contactsLookup = FakeContactsLookup(),
            bookableResourceLookup = FakeBookableResourceLookup(),
        )
        val sdkCaller = SdkCaller(NoOpCrashReport)
        return CalendarManager(
            accountRepository = accountRepository,
            calendarRepository = calendarRepository,
            eventRepository = eventRepository,
            meetManager = MeetManager(appEnvironment = AppEnvironment.Prod, crashReport = NoOpCrashReport),
            calendarBookableResourcesManager = CalendarBookableResourcesManager(
                calendarRepository = calendarRepository,
                database = database,
                sdkCaller = sdkCaller,
                appEnvironment = AppEnvironment.Prod,
                crashReport = NoOpCrashReport,
            ),
            calendarOrganisationsManager = CalendarOrganisationsManager(
                calendarRepository = calendarRepository,
                database = database,
                sdkCaller = sdkCaller,
                appEnvironment = AppEnvironment.Prod,
                crashReport = NoOpCrashReport,
            ),
            sdkCaller = sdkCaller,
        )
    }

    private fun createAccountRepository(): AccountRepository = AccountRepository(
        accountDao = database.accountDao(),
        authDataSource = AuthDataSource(HttpClient(MockEngine { error("AuthDataSource must not be called in this test") })),
    )

    private class BlockingCalendarSyncRemoteSource(
        private val calendarUrl: String,
        private val delegate: CalendarSyncRemoteSource = FakeCaldavClient(),
    ) : CalendarSyncRemoteSource by delegate {
        val firstSyncStarted = CompletableDeferred<Unit>()
        val releaseFirstSync = CompletableDeferred<Unit>()
        var syncCollectionCalls = 0
            private set

        override suspend fun discover(credentials: DavAccount): RemoteDavDiscovery {
            return RemoteDavDiscovery(
                userEmails = null,
                calendars = listOf(RemoteDavCalendar(url = calendarUrl, displayName = "Team")),
            )
        }

        override suspend fun syncCollection(
            credentials: DavAccount,
            calendarUrl: String,
            syncToken: String?,
        ): RemoteEventSyncDelta {
            syncCollectionCalls += 1
            if (syncCollectionCalls == 1) {
                firstSyncStarted.complete(Unit)
                releaseFirstSync.await()
            }
            return RemoteEventSyncDelta(syncToken = syncToken, items = emptyList())
        }
    }
}
