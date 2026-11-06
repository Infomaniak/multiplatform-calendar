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

import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.BookableResourceEntity
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_calendar.core.repository.event.FakeCaldavClient
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.appenvironment.AppEnvironment
import com.infomaniak.multiplatform_core.crashreport.NoOpCrashReport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarBookableResourcesManagerTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var manager: CalendarBookableResourcesManager

    @BeforeTest
    fun setUp() {
        database = DatabaseProviderFactory.createTestDatabaseConfig()
            .getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
        val calendarRepository = CalendarRepository(
            caldavClient = FakeCaldavClient(),
            accountDao = database.accountDao(),
            calendarDao = database.calendarDao(),
            crashReport = NoOpCrashReport,
            eventDao = database.eventDao(),
        )
        manager = CalendarBookableResourcesManager(
            calendarRepository = calendarRepository,
            database = database,
            sdkCaller = SdkCaller(NoOpCrashReport),
            appEnvironment = AppEnvironment.Prod,
            crashReport = NoOpCrashReport,
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun observeResources_readsTheAccountOfTheCalendar() = runTest {
        val workCalendar = insertCalendar(AccountId(1), "https://dav.example/cal/work/")
        val homeCalendar = insertCalendar(AccountId(2), "https://dav.example/cal/home/")
        insertResources(resource(AccountId(1), "room-a", "Room A"), resource(AccountId(2), "room-b", "Room B"))

        assertEquals(listOf("Room A"), manager.observeResources(workCalendar).first().map { it.name })
        assertEquals(listOf("Room B"), manager.observeResources(homeCalendar).first().map { it.name })
    }

    @Test
    fun observeResources_isEmpty_whenTheCalendarIsMissing() = runTest {
        insertResources(resource(AccountId(1), "room-a", "Room A"))

        assertEquals(emptyList(), manager.observeResources(CalendarId("https://dav.example/cal/missing/")).first())
    }

    @Test
    fun observeResources_becomesEmpty_whenTheAccountOfTheCalendarIsRemoved() = runTest {
        val calendarId = insertCalendar(AccountId(1), "https://dav.example/cal/work/")
        insertResources(resource(AccountId(1), "room-a", "Room A"))
        val names = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch { manager.observeResources(calendarId).map { it.map { resource -> resource.name } }.collect(names::send) }

        assertEquals(listOf("Room A"), names.receive())

        database.accountDao().delete(AccountId(1))
        assertEquals(emptyList(), names.receive())
        collector.cancel()
    }

    @Test
    fun observeResource_readsTheAccountOfTheCalendar() = runTest {
        val calendarId = insertCalendar(AccountId(2), "https://dav.example/cal/home/")
        insertResources(resource(AccountId(1), "room", "Room of account 1"), resource(AccountId(2), "room", "Room of account 2"))

        assertEquals("Room of account 2", manager.observeResource(calendarId, BookableResourceUuid("room")).first()?.name)
    }

    @Test
    fun observeResource_isNull_whenTheCalendarIsMissing() = runTest {
        insertResources(resource(AccountId(1), "room", "Room"))

        assertNull(manager.observeResource(CalendarId("https://dav.example/cal/missing/"), BookableResourceUuid("room")).first())
    }

    private suspend fun insertCalendar(accountId: AccountId, url: String): CalendarId {
        val calendarId = CalendarId(url)
        database.accountDao().insert(AccountEntity(accountId))
        database.calendarDao().insert(CalendarEntity(id = calendarId, accountId = accountId, displayName = url, color = null))
        return calendarId
    }

    private suspend fun insertResources(vararg resources: BookableResourceEntity) {
        database.bookableResourceDao().upsertResources(resources.toList())
    }

    private fun resource(accountId: AccountId, uuid: String, name: String) = BookableResourceEntity(
        accountId = accountId,
        uuid = BookableResourceUuid(uuid),
        name = name,
        color = null,
        description = null,
        location = null,
        floor = null,
        capacity = null,
        equipments = emptyList(),
    )
}
