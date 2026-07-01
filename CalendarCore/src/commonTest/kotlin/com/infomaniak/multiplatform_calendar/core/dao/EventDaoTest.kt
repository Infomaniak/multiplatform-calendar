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
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventDaoTest : RobolectricTestsBase() {

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
    fun upsertAndGetEvent_returnsPersistedEvent() = runTest {
        val accountId = AccountId(1)
        val calendarId = CalendarId("calendar://visible")
        val event = createEvent(
            eventId = EventId("event://1"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
        )
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)

        eventDao.upsert(listOf(event))

        assertEquals(event, eventDao.getEvent(event.id))
    }

    @Test
    fun observeEvents_returnsEventsOrderedByStart() = runTest {
        val accountId = AccountId(1)
        val calendarId = CalendarId("calendar://visible")
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)

        val lateEvent = createEvent(
            eventId = EventId("event://late"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 12, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 13, 0),
        )
        val earlyEvent = createEvent(
            eventId = EventId("event://early"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 8, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 9, 0),
        )
        eventDao.upsert(listOf(lateEvent, earlyEvent))

        val observed = eventDao.observeEvents(calendarId).first()

        assertEquals(listOf(earlyEvent.id, lateEvent.id), observed.map(EventEntity::id))
    }

    @Test
    fun observeVisibleInRange_filtersByAccountVisibilityAndOverlap() = runTest {
        val account1 = AccountId(1)
        val account2 = AccountId(2)
        val visibleCalendar = CalendarId("calendar://visible")
        val hiddenCalendar = CalendarId("calendar://hidden")
        val otherAccountCalendar = CalendarId("calendar://other")

        seedCalendar(accountId = account1, calendarId = visibleCalendar, isVisible = true)
        seedCalendar(accountId = account1, calendarId = hiddenCalendar, isVisible = false)
        seedCalendar(accountId = account2, calendarId = otherAccountCalendar, isVisible = true)

        val inRangeVisible = createEvent(
            eventId = EventId("event://in-range-visible"),
            calendarId = visibleCalendar,
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
        )
        val inRangeHidden = createEvent(
            eventId = EventId("event://in-range-hidden"),
            calendarId = hiddenCalendar,
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
        )
        val inRangeOtherAccount = createEvent(
            eventId = EventId("event://in-range-other-account"),
            calendarId = otherAccountCalendar,
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
        )
        val outsideRange = createEvent(
            eventId = EventId("event://outside-range"),
            calendarId = visibleCalendar,
            dtStart = LocalDateTime(2026, 6, 29, 15, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 16, 0),
        )
        eventDao.upsert(listOf(inRangeVisible, inRangeHidden, inRangeOtherAccount, outsideRange))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account1),
            start = LocalDateTime(2026, 6, 29, 8, 30),
            end = LocalDateTime(2026, 6, 29, 12, 0),
        ).first()

        assertEquals(listOf(inRangeVisible.id), observed.map { it.event.id })
        assertEquals(listOf(visibleCalendar), observed.map { it.calendar.id })
    }


    @Test
    fun observeEventWithCalendar_returnsEventAndCalendar() = runTest {
        val accountId = AccountId(7)
        val calendarId = CalendarId("calendar://single")
        val eventId = EventId("event://single")
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)
        val event = createEvent(
            eventId = eventId,
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 11, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 12, 0),
        )
        eventDao.upsert(listOf(event))

        val observed = eventDao.observeEventWithCalendar(eventId).first()

        assertEquals(event, observed?.event)
        assertEquals(calendarId, observed?.calendar?.id)
        assertEquals(accountId, observed?.calendar?.accountId)
    }

    @Test
    fun deleteEvent_removesEvent() = runTest {
        val accountId = AccountId(3)
        val calendarId = CalendarId("calendar://delete")
        val eventId = EventId("event://delete")
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)
        eventDao.upsert(
            listOf(
                createEvent(
                    eventId = eventId,
                    calendarId = calendarId,
                    dtStart = LocalDateTime(2026, 6, 29, 13, 0),
                    dtEndEffective = LocalDateTime(2026, 6, 29, 14, 0),
                ),
            ),
        )

        eventDao.deleteEvent(eventId)

        assertNull(eventDao.getEvent(eventId))
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
    ) = EventEntity(
        id = eventId,
        calendarId = calendarId,
        summary = "Summary ${eventId.url}",
        dtStart = dtStart,
        dtEndEffective = dtEndEffective,
        etag = "etag-${eventId.url}",
        rawIcs = "BEGIN:VEVENT\\nUID:${eventId.url}\\nEND:VEVENT",
    )
}
