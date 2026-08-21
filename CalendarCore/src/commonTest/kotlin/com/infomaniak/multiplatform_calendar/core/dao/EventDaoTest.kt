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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.core.utils.seedEvents
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        seedEvents(listOf(event))

        assertEquals(event, eventDao.getEvent(event.id))
    }

    @Test
    fun upsertEventsWithRawIcs_replacesTheWholeOverrideSetOfTheMaster() = runTest {
        val accountId = AccountId(1)
        val calendarId = CalendarId("calendar://visible")
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)
        val master = createEvent(
            eventId = EventId("event://series"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
        )

        eventDao.upsert(
            listOf(
                EventWithRawIcs(
                    master,
                    "",
                    listOf(
                        createOverride(master.id, LocalDateTime(2026, 6, 17, 10, 0)),
                        createOverride(master.id, LocalDateTime(2026, 6, 18, 10, 0)),
                    ),
                ),
            ),
        )

        assertEquals(2, eventDao.getOverridesOf(master.id).size)

        // The 06-18 instance disappeared server-side: leaving no tombstone, only the rewrite removes it.
        val moved = createOverride(master.id, LocalDateTime(2026, 6, 17, 10, 0), movedTo = LocalDateTime(2026, 6, 17, 15, 0))
        eventDao.upsert(listOf(EventWithRawIcs(master, "", listOf(moved))))

        assertEquals(listOf(moved), eventDao.getOverridesOf(master.id))

        eventDao.upsert(listOf(EventWithRawIcs(master, "", overrides = emptyList())))

        assertTrue(eventDao.getOverridesOf(master.id).isEmpty())
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
        seedEvents(listOf(lateEvent, earlyEvent))

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
        seedEvents(listOf(inRangeVisible, inRangeHidden, inRangeOtherAccount, outsideRange))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account1),
            startInstantMs = LocalDateTime(2026, 6, 29, 8, 30).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 6, 29, 12, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 6, 29, 8, 30),
            endLocalDateTime = LocalDateTime(2026, 6, 29, 12, 0),
        ).first()

        assertEquals(listOf(inRangeVisible.id), observed.map { it.event.id })
        assertEquals(listOf(visibleCalendar), observed.map { it.calendar.id })
    }

    @Test
    fun observeVisibleInRange_zonedEvent_overlapsBasedOnUtcInstant() = runTest {
        // Query range in device (Tokyo) wall-clock 09:00-12:00 = 00:00-03:00 UTC.
        // Two events stored in Paris (UTC+2 in summer):
        //   in-range: Paris 02:00-03:00 (= UTC 00:00-01:00) → OVERLAPS
        //   out-of-range: Paris 12:00-13:00 (= UTC 10:00-11:00) → does not overlap
        val account = AccountId(10)
        val calendarId = CalendarId("calendar://zoned")
        val paris = TimeZone.of("Europe/Paris")
        val tokyo = TimeZone.of("Asia/Tokyo")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val inRange = createEvent(
            eventId = EventId("event://paris-inrange"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 2, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 3, 0),
            startZone = paris,
            endZone = paris,
        )
        val outside = createEvent(
            eventId = EventId("event://paris-outside"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 12, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 13, 0),
            startZone = paris,
            endZone = paris,
        )
        seedEvents(listOf(inRange, outside))

        val queryStart = LocalDateTime(2026, 6, 29, 9, 0)
        val queryEnd = LocalDateTime(2026, 6, 29, 12, 0)
        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = queryStart.toEpochMs(tokyo),
            endInstantMs = queryEnd.toEpochMs(tokyo),
            startLocalDateTime = queryStart,
            endLocalDateTime = queryEnd,
        ).first()

        assertEquals(listOf(inRange.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_floatingEvent_overlapsBasedOnWallClock() = runTest {
        // Floating events have no absolute instant — comparison must fall back to the wall-clock
        // branch that uses the device zone at query time (re-anchors automatically on travel).
        val account = AccountId(11)
        val calendarId = CalendarId("calendar://floating")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val inRange = createEvent(
            eventId = EventId("event://float-inrange"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 10, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 11, 0),
            startZone = null,
            endZone = null,
        )
        val outside = createEvent(
            eventId = EventId("event://float-outside"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 20, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 21, 0),
            startZone = null,
            endZone = null,
        )
        seedEvents(listOf(inRange, outside))

        // Absolute bounds are arbitrary here since the floating branch ignores them; only the
        // wall-clock ones matter for these rows.
        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = 0L,
            endInstantMs = Long.MAX_VALUE,
            startLocalDateTime = LocalDateTime(2026, 6, 29, 9, 0),
            endLocalDateTime = LocalDateTime(2026, 6, 29, 12, 0),
        ).first()

        assertEquals(listOf(inRange.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_mixesAnchoredAndFloating_inSameQuery() = runTest {
        // Both branches must contribute; ordering puts anchored (non-null dtStartInstantMs) first
        // then floating, sorted by their respective wall-clock.
        val account = AccountId(12)
        val calendarId = CalendarId("calendar://mixed")
        val paris = TimeZone.of("Europe/Paris")
        val tokyo = TimeZone.of("Asia/Tokyo")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val zoned = createEvent(
            eventId = EventId("event://mixed-zoned"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 2, 30),
            dtEndEffective = LocalDateTime(2026, 6, 29, 3, 0),
            startZone = paris,
            endZone = paris,
        )
        val floating = createEvent(
            eventId = EventId("event://mixed-floating"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 10, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 11, 0),
            startZone = null,
            endZone = null,
        )
        seedEvents(listOf(zoned, floating))

        val queryStart = LocalDateTime(2026, 6, 29, 9, 0)
        val queryEnd = LocalDateTime(2026, 6, 29, 12, 0)
        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = queryStart.toEpochMs(tokyo),
            endInstantMs = queryEnd.toEpochMs(tokyo),
            startLocalDateTime = queryStart,
            endLocalDateTime = queryEnd,
        ).first()

        assertTrue(observed.map { it.event.id }.containsAll(listOf(zoned.id, floating.id)))
        assertEquals(2, observed.size)
        // Anchored (non-null dtStartInstantMs) sorts before floating regardless of wall-clock.
        assertEquals(listOf(zoned.id, floating.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_ordersAnchoredBeforeFloating_eachSortedInternally() = runTest {
        // ORDER BY dtStartInstantMs IS NULL, dtStartInstantMs ASC, dtStart ASC:
        // anchored events first (by absolute instant), then floating events (by wall-clock).
        val account = AccountId(13)
        val calendarId = CalendarId("calendar://ordering")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val anchoredEarly = createEvent(
            eventId = EventId("event://anchored-early"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 8, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 9, 0),
        ) // UTC anchored by default
        val anchoredLate = createEvent(
            eventId = EventId("event://anchored-late"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 10, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 11, 0),
        )
        val floatingEarly = createEvent(
            eventId = EventId("event://floating-early"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 7, 0), // earlier wall-clock than the anchored ones...
            dtEndEffective = LocalDateTime(2026, 6, 29, 8, 0),
            startZone = null,
            endZone = null,
        )
        val floatingLate = createEvent(
            eventId = EventId("event://floating-late"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 12, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 13, 0),
            startZone = null,
            endZone = null,
        )
        // Insert scrambled to prove the ORDER BY (not insertion order) drives the result.
        seedEvents(listOf(floatingLate, anchoredLate, floatingEarly, anchoredEarly))

        val queryStart = LocalDateTime(2026, 6, 29, 0, 0)
        val queryEnd = LocalDateTime(2026, 6, 30, 0, 0)
        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = queryStart.toEpochMs(TimeZone.UTC),
            endInstantMs = queryEnd.toEpochMs(TimeZone.UTC),
            startLocalDateTime = queryStart,
            endLocalDateTime = queryEnd,
        ).first()

        // ...yet floatingEarly still comes after both anchored events (anchored group is always first).
        assertEquals(
            listOf(anchoredEarly.id, anchoredLate.id, floatingEarly.id, floatingLate.id),
            observed.map { it.event.id },
        )
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
        seedEvents(listOf(event))

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
        seedEvents(
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

    @Test
    fun deleteEvent_cascadeDeletesRawIcs() = runTest {
        val accountId = AccountId(4)
        val calendarId = CalendarId("calendar://cascade")
        val eventId = EventId("event://cascade")
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)
        val event = createEvent(
            eventId = eventId,
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
        )
        eventDao.upsert(listOf(EventWithRawIcs(event, "BEGIN:VEVENT")))
        assertEquals("BEGIN:VEVENT", eventDao.getRawIcs(eventId))

        eventDao.deleteEvent(eventId)

        assertNull(eventDao.getEvent(eventId))
        assertNull(eventDao.getRawIcs(eventId))
    }

    @Test
    fun upsert_updatesRawIcs_whenEventIsReupserted() = runTest {
        val accountId = AccountId(5)
        val calendarId = CalendarId("calendar://update-rawics")
        val eventId = EventId("event://update-rawics")
        seedCalendar(accountId = accountId, calendarId = calendarId, isVisible = true)
        val event = createEvent(
            eventId = eventId,
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
        )
        eventDao.upsert(listOf(EventWithRawIcs(event, "BEGIN:VEVENT\nV1")))
        assertEquals("BEGIN:VEVENT\nV1", eventDao.getRawIcs(eventId))

        eventDao.upsert(listOf(EventWithRawIcs(event, "BEGIN:VEVENT\nV2")))

        assertEquals("BEGIN:VEVENT\nV2", eventDao.getRawIcs(eventId))
    }

    private suspend fun seedEvents(events: List<EventEntity>) {
        eventDao.seedEvents(events)
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

    @Test
    fun observeVisibleInRange_recurringMaster_matchesOnLaterOccurrenceBeyondDtStart() = runTest {
        val account = AccountId(30)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        // DAILY infinite series starting far before the window: dtStart's own occurrence is out of
        // range but the series still overlaps → the master must be returned.
        val master = createRecurringEvent(
            eventId = EventId("event://daily-infinite"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2020, 1, 1, 9, 0),
            dtEndEffective = LocalDateTime(2020, 1, 1, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily),
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 6, 29, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 6, 30, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 6, 29, 0, 0),
            endLocalDateTime = LocalDateTime(2026, 6, 30, 0, 0),
        ).first()

        assertEquals(listOf(master.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_recurringMaster_windowBeforeDtStart_excluded() = runTest {
        val account = AccountId(31)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val master = createRecurringEvent(
            eventId = EventId("event://daily-future"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily),
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 6, 1, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 6, 2, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 6, 1, 0, 0),
            endLocalDateTime = LocalDateTime(2026, 6, 2, 0, 0),
        ).first()

        assertTrue(observed.isEmpty())
    }

    @Test
    fun observeVisibleInRange_recurringMaster_finiteUntil_excludedAfterUpperBound() = runTest {
        val account = AccountId(32)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        // DAILY UNTIL 2026-06-10 → the whole series ends well before the July window.
        val master = createRecurringEvent(
            eventId = EventId("event://daily-until"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 1, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 1, 10, 0),
            rrule = RecurrenceRule(
                freq = Frequency.Daily,
                until = RecurrenceUntil.DateTimeUtc(
                    LocalDateTime(2026, 6, 10, 9, 0).toInstant(TimeZone.UTC),
                ),
            ),
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 7, 1, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 7, 2, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 7, 1, 0, 0),
            endLocalDateTime = LocalDateTime(2026, 7, 2, 0, 0),
        ).first()

        assertTrue(observed.isEmpty())
    }

    @Test
    fun observeVisibleInRange_recurringMaster_finiteUntil_includedWithinBound() = runTest {
        val account = AccountId(33)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val master = createRecurringEvent(
            eventId = EventId("event://daily-until-in"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 1, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 1, 10, 0),
            rrule = RecurrenceRule(
                freq = Frequency.Daily,
                until = RecurrenceUntil.DateTimeUtc(
                    LocalDateTime(2026, 6, 30, 9, 0).toInstant(TimeZone.UTC),
                ),
            ),
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 6, 15, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 6, 16, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 6, 15, 0, 0),
            endLocalDateTime = LocalDateTime(2026, 6, 16, 0, 0),
        ).first()

        assertEquals(listOf(master.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_recurringFloatingMaster_matchesOnWallClock() = runTest {
        val account = AccountId(34)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        // Floating DAILY series (no time-zone) starting before the window → matches on wall-clock.
        val master = createRecurringEvent(
            eventId = EventId("event://daily-floating"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2020, 1, 1, 9, 0),
            dtEndEffective = LocalDateTime(2020, 1, 1, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily),
            startZone = null,
            endZone = null,
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 6, 29, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 6, 30, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 6, 29, 0, 0),
            endLocalDateTime = LocalDateTime(2026, 6, 30, 0, 0),
        ).first()

        assertEquals(listOf(master.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_recurringMaster_countUnknown_includedInFarFutureWindow() = runTest {
        val account = AccountId(35)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        // COUNT without UNTIL → FiniteDeferred upper bound (open at sync); the range keeps the master and lets
        // the expander apply the cut-off later.
        val master = createRecurringEvent(
            eventId = EventId("event://daily-count"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 1, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 1, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2027, 1, 1, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2027, 1, 2, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2027, 1, 1, 0, 0),
            endLocalDateTime = LocalDateTime(2027, 1, 2, 0, 0),
        ).first()

        assertEquals(listOf(master.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_recurringAllDayMaster_matchedWithinDeviceZonePadding() = runTest {
        val account = AccountId(36)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        // All-day bounds are computed at UTC midnight but padded by ±14h (widest UTC offset) so a device zone
        // ahead of UTC — where 2026-06-15 starts before UTC midnight — still matches. A window on 2026-06-14,
        // before the unpadded first-occurrence instant but inside that padding, must keep the master; the
        // expander decides afterwards. Without the padding the low bound would be 2026-06-15T00:00Z and exclude it.
        val dtStart = LocalDateTime(2026, 6, 15, 0, 0)
        val dtEnd = LocalDateTime(2026, 6, 16, 0, 0)
        val timing = EventTimingEntity(
            dtStart = dtStart,
            dtEnd = dtEnd,
            dtEndEffective = dtEnd,
            startTimeZone = null,
            endTimeZone = null,
            dtStartInstantMs = dtStart.toEpochMs(TimeZone.UTC),
            dtEndInstantMs = dtEnd.toEpochMs(TimeZone.UTC),
            isAllDay = true,
        )
        val rrule = RecurrenceRule(freq = Frequency.Daily)
        val master = EventEntity(
            id = EventId("event://all-day-daily"),
            calendarId = calendarId,
            summary = "All-day daily",
            timing = timing,
            rrule = rrule,
            hasRecurrence = true,
            recurrenceBounds = checkNotNull(toRecurrenceBoundsEntity(timing = timing, recurrenceRule = rrule, rDates = emptyList())),
            etag = "etag-all-day-daily",
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 6, 14, 12, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 6, 14, 20, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 6, 14, 12, 0),
            endLocalDateTime = LocalDateTime(2026, 6, 14, 20, 0),
        ).first()

        assertEquals(listOf(master.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_rdateOnlyMaster_isIncludedWithoutRrule() = runTest {
        val account = AccountId(37)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val timing = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 29, 9, 0),
            dtEndEffective = LocalDateTime(2026, 6, 29, 10, 0),
            startTimeZone = TimeZone.UTC.id,
            endTimeZone = TimeZone.UTC.id,
            dtStartInstantMs = LocalDateTime(2026, 6, 29, 9, 0).toEpochMs(TimeZone.UTC),
            dtEndInstantMs = LocalDateTime(2026, 6, 29, 10, 0).toEpochMs(TimeZone.UTC),
        )
        val rDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-07-10T09:00:00Z"), TimeZone.UTC.id))
        val master = EventEntity(
            id = EventId("event://rdate-only"),
            calendarId = calendarId,
            summary = "RDATE only",
            timing = timing,
            rDates = rDates,
            hasRecurrence = true,
            recurrenceBounds = toRecurrenceBoundsEntity(timing, recurrenceRule = null, rDates = rDates),
            etag = "etag-rdate-only",
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 7, 10, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 7, 11, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 7, 10, 0, 0),
            endLocalDateTime = LocalDateTime(2026, 7, 11, 0, 0),
        ).first()

        assertEquals(listOf(master.id), observed.map { it.event.id })
    }

    @Test
    fun observeVisibleInRange_rdateBeforeDtStart_usesFirstOccurrenceLowBound() = runTest {
        val account = AccountId(38)
        val calendarId = CalendarId("calendar://recurring")
        seedCalendar(accountId = account, calendarId = calendarId, isVisible = true)

        val timing = EventTimingEntity(
            dtStart = LocalDateTime(2026, 7, 10, 9, 0),
            dtEndEffective = LocalDateTime(2026, 7, 10, 10, 0),
            startTimeZone = TimeZone.UTC.id,
            endTimeZone = TimeZone.UTC.id,
            dtStartInstantMs = LocalDateTime(2026, 7, 10, 9, 0).toEpochMs(TimeZone.UTC),
            dtEndInstantMs = LocalDateTime(2026, 7, 10, 10, 0).toEpochMs(TimeZone.UTC),
        )
        val rDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-06-10T09:00:00Z"), TimeZone.UTC.id))
        val master = EventEntity(
            id = EventId("event://rdate-before-dtstart"),
            calendarId = calendarId,
            summary = "RDATE before DTSTART",
            timing = timing,
            rDates = rDates,
            hasRecurrence = true,
            recurrenceBounds = toRecurrenceBoundsEntity(timing, recurrenceRule = null, rDates = rDates),
            etag = "etag-rdate-before",
        )
        seedEvents(listOf(master))

        val observed = eventDao.observeVisibleInRange(
            accountIds = setOf(account),
            startInstantMs = LocalDateTime(2026, 6, 10, 0, 0).toEpochMs(TimeZone.UTC),
            endInstantMs = LocalDateTime(2026, 6, 11, 0, 0).toEpochMs(TimeZone.UTC),
            startLocalDateTime = LocalDateTime(2026, 6, 10, 0, 0),
            endLocalDateTime = LocalDateTime(2026, 6, 11, 0, 0),
        ).first()

        assertEquals(listOf(master.id), observed.map { it.event.id })
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
            dtStartInstantMs = startZone?.let { dtStart.toEpochMs(it) },
            dtEndInstantMs = endZone?.let { dtEndEffective.toEpochMs(it) },
        ),
        etag = "etag-${eventId.url}",
    )

    private fun createRecurringEvent(
        eventId: EventId,
        calendarId: CalendarId,
        dtStart: LocalDateTime,
        dtEndEffective: LocalDateTime,
        rrule: RecurrenceRule,
        startZone: TimeZone? = TimeZone.UTC,
        endZone: TimeZone? = TimeZone.UTC,
    ): EventEntity {
        val timing = EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = dtEndEffective,
            startTimeZone = startZone?.id,
            endTimeZone = endZone?.id,
            dtStartInstantMs = startZone?.let { dtStart.toEpochMs(it) },
            dtEndInstantMs = endZone?.let { dtEndEffective.toEpochMs(it) },
        )
        return EventEntity(
            id = eventId,
            calendarId = calendarId,
            summary = "Summary ${eventId.url}",
            timing = timing,
            rrule = rrule,
            hasRecurrence = true,
            recurrenceBounds = checkNotNull(toRecurrenceBoundsEntity(timing = timing, recurrenceRule = rrule, rDates = emptyList())),
            etag = "etag-${eventId.url}",
        )
    }

    private fun LocalDateTime.toEpochMs(zone: TimeZone): Long = toInstant(zone).toEpochMilliseconds()

    private fun createOverride(
        masterId: EventId,
        originalStart: LocalDateTime,
        movedTo: LocalDateTime = originalStart,
    ): EventOverrideEntity {
        val originalEnd = LocalDateTime(originalStart.date, LocalTime(originalStart.hour + 1, originalStart.minute))
        val movedEnd = LocalDateTime(movedTo.date, LocalTime(movedTo.hour + 1, movedTo.minute))
        return EventOverrideEntity(
            masterId = masterId,
            recurrenceKey = RecurrenceKey.Utc(originalStart.toInstant(TimeZone.UTC)),
            originalStartInstantMs = originalStart.toEpochMs(TimeZone.UTC),
            originalEndInstantMs = originalEnd.toEpochMs(TimeZone.UTC),
            originalStartLocalDateTime = originalStart,
            originalEndLocalDateTime = originalEnd,
            content = EventContentEntity(
                summary = "Moved instance",
                timing = EventTimingEntity(
                    dtStart = movedTo,
                    dtEndEffective = movedEnd,
                    startTimeZone = TimeZone.UTC.id,
                    endTimeZone = TimeZone.UTC.id,
                    dtStartInstantMs = movedTo.toEpochMs(TimeZone.UTC),
                    dtEndInstantMs = movedEnd.toEpochMs(TimeZone.UTC),
                ),
            ),
        )
    }
}
