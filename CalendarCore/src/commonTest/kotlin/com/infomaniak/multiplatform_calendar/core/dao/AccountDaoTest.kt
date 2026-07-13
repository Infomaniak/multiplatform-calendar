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
package com.infomaniak.multiplatform_calendar.core.dao

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.CalendarDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountDaoTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var calendarDao: CalendarDao
    private lateinit var eventDao: EventDao

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
        accountDao = database.accountDao()
        calendarDao = database.calendarDao()
        eventDao = database.eventDao()
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun getAccountIdByEventId_returnsOwningAccountId() = runTest {
        val accountId = AccountId(42)
        val calendarId = CalendarId("calendar://owner")
        val eventId = EventId("event://owner")
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)
        eventDao.upsert(
            listOf(
                createEvent(
                    eventId = eventId,
                    calendarId = calendarId,
                    dtStart = LocalDateTime(2026, 6, 29, 9, 0),
                    dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
                ),
            ),
        )

        assertEquals(accountId, accountDao.getAccountIdByEventId(eventId))
    }

    @Test
    fun getAccountIdByEventId_returnsNullWhenEventIsMissing() = runTest {
        assertNull(accountDao.getAccountIdByEventId(EventId("event://missing")))
    }

    private suspend fun seedCalendar(accountId: AccountId, calendarId: CalendarId, isVisible: Boolean) {
        accountDao.insert(AccountEntity(id = accountId))
        calendarDao.upsert(
            listOf(
                CalendarEntity(
                    id = calendarId,
                    accountId = accountId,
                    displayName = "Calendar ${calendarId.url}",
                    color = null,
                    isVisible = isVisible,
                ),
            ),
        )
    }

    private fun createEvent(
        eventId: EventId,
        calendarId: CalendarId,
        dtStart: LocalDateTime,
        dtEndEffective: LocalDateTime,
        startZone: TimeZone? = TimeZone.UTC,
        endZone: TimeZone? = TimeZone.UTC,
    ) = EventEntity(
        id = eventId,
        calendarId = calendarId,
        summary = "Summary ${eventId.url}",
        timing = EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = dtEndEffective,
            startTimeZone = startZone?.id,
            endTimeZone = endZone?.id,
            dtStartInstantMs = startZone?.let { dtStart.toInstant(it).toEpochMilliseconds() },
            dtEndInstantMs = endZone?.let { dtEndEffective.toInstant(it).toEpochMilliseconds() },
        ),
        etag = "etag-${eventId.url}",
        rawIcs = "BEGIN:VEVENT\\nUID:${eventId.url}\\nEND:VEVENT",
    )
}

