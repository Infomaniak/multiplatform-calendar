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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.local.entity.RecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventSummary
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ParticipationStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.startWallClock
import com.infomaniak.multiplatform_calendar.core.domain.model.event.zonedBounds
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

internal class EventRepositoryObserveTest : EventRepositoryTestBase() {

    /**
     * Regression: `observeVisibleEvents(zone = X)` must feed the wall-clock range bounds in [X]
     * to the DAO so floating events are filtered against the caller-provided zone, not the device
     * zone. Otherwise `observeVisibleDaySlices(timeZone = X)` (which forwards the same [X]) would
     * filter events in one zone and slice them in another, producing inconsistent results.
     */
    @Test
    fun observeVisibleEvents_usesProvidedZoneForFloatingBounds() = runTest {
        seedCalendar()

        // Floating event: wall-clock 10:00 → 11:00, no zone (dtStartInstantMs = null).
        eventDao().upsert(listOf(EventWithRawIcs(floatingEvent(CALENDAR_ID), "")))

        // Same absolute Instant range, but interpreted in different zones for the SQL wall-clock bounds:
        //  - UTC:                    10:00-10:30 wall → OVERLAPS the 10:00-11:00 floating event
        //  - Paris (UTC+2 in summer):12:00-12:30 wall → does NOT overlap (starts after event ends)
        val rangeStart = LocalDateTime(2026, 6, 15, 10, 0).toInstant(TimeZone.UTC)
        val rangeEnd = LocalDateTime(2026, 6, 15, 10, 30).toInstant(TimeZone.UTC)

        val utcResult = repository.observeVisibleEvents(
            accountIds = setOf(ACCOUNT_ID),
            start = rangeStart,
            end = rangeEnd,
            zone = TimeZone.UTC,
        ).first()
        val parisResult = repository.observeVisibleEvents(
            accountIds = setOf(ACCOUNT_ID),
            start = rangeStart,
            end = rangeEnd,
            zone = TimeZone.of("Europe/Paris"),
        ).first()

        assertEquals(1, utcResult.size, "UTC bounds (10:00-10:30 wall) should overlap 10:00-11:00")
        assertEquals(0, parisResult.size, "Paris bounds (12:00-12:30 wall) should not overlap 10:00-11:00")
    }

    /**
     * Pipeline: a `DAILY` COUNT=5 master synced once must surface as 5 distinct occurrences over the
     * 7-day window in `observeVisibleDaySlices` — one per day, each a slice under its own date, with a
     * stable synthetic id `occurrence#key#masterId`.
     */
    @Test
    fun observeVisibleDaySlices_expandsDailyRecurringMasterIntoOccurrences() = runTest {
        seedCalendar()

        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        val dtEnd = LocalDateTime(2026, 6, 15, 11, 0)
        val master = EventEntity(
            id = EventId("event://daily"),
            calendarId = CALENDAR_ID,
            content = EventContentEntity(
                summary = "Daily 10-11",
                timing = EventTimingEntity(
                    dtStart = dtStart,
                    dtEndEffective = dtEnd,
                    startTimeZone = TimeZone.UTC.id,
                    endTimeZone = TimeZone.UTC.id,
                    dtStartInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds(),
                    dtEndInstantMs = dtEnd.toInstant(TimeZone.UTC).toEpochMilliseconds(),
                ),
            ),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            recurrenceBounds = RecurrenceBoundsEntity(
                firstOccurrenceInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds(),
                recurrenceBoundKind = RecurrenceBoundKind.FiniteDeferred,
            ),
            etag = "1",
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val slicesByDay = repository.observeVisibleDaySlices(
            accountIds = setOf(ACCOUNT_ID),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        val occurrenceIds = slicesByDay.values.flatten().map { it.event.occurrenceId.value }
        assertEquals(5, occurrenceIds.size, "DAILY COUNT=5 must yield 5 occurrences")
        assertEquals(occurrenceIds.toSet().size, occurrenceIds.size, "occurrence ids must be unique")
        assertTrue(occurrenceIds.all { it.endsWith("#event://daily") }, "ids must name their master")
        assertEquals(5, slicesByDay.keys.size, "each occurrence lands on its own day")
    }

    /**
     * End-to-end guard for the database-to-expander path: the Room relation, the override mapper and the
     * recurrence-key lookup all sit between the DAO and the rendered occurrence, and none of them is
     * exercised by the expander's own tests, which inject an already-built series.
     */
    @Test
    fun observeVisibleDaySlices_rendersAStoredOverrideInPlaceOfItsOccurrence() = runTest {
        seedCalendar()

        val master = dailyMasterEntity(EventId("event://daily-overridden"), CALENDAR_ID)
        val overriddenSlot = LocalDateTime(2026, 6, 17, 10, 0)
        val override = overrideEntity(master.id, originalStart = overriddenSlot, movedTo = LocalDateTime(2026, 6, 17, 15, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val slicesByDay = repository.observeVisibleDaySlices(
            accountIds = setOf(ACCOUNT_ID),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        val events = slicesByDay.values.flatten().map { it.event }
        assertEquals(5, events.size, "the override replaces its occurrence, it does not add one")

        val expectedId = "occurrence#${RecurrenceKey.Utc(overriddenSlot.toInstant(TimeZone.UTC)).canonical}#${master.id.url}"
        val rendered = events.single { it.occurrenceId.value == expectedId }
        assertEquals("Moved instance", rendered.title, "the override's own content must reach the rendered occurrence")
        assertEquals(
            zonedBounds(LocalDateTime(2026, 6, 17, 15, 0), LocalDateTime(2026, 6, 17, 16, 0), TimeZone.UTC),
            rendered.timing.bounds,
            "and its own, moved timing",
        )
        assertEquals(
            1,
            events.count { it.timing.bounds.startWallClock.date == LocalDateTime(2026, 6, 17, 0, 0).date },
            "the theoretical 10:00 slot must be gone, not doubled",
        )
    }

    @Test
    fun observeVisibleDaySlices_dropsAStoredCancelledOverride() = runTest {
        seedCalendar()

        val master = dailyMasterEntity(EventId("event://daily-cancelled"), CALENDAR_ID)
        val override = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 17, 10, 0),
            status = EventStatus.CANCELLED,
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val slicesByDay = repository.observeVisibleDaySlices(
            accountIds = setOf(ACCOUNT_ID),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        val days = slicesByDay.values.flatten().map { it.event.timing.bounds.startWallClock.date }
        assertEquals(4, days.size, "a cancelled override deletes its occurrence")
        assertTrue(LocalDateTime(2026, 6, 17, 0, 0).date !in days, "and it is the overridden day that disappears")
    }

    @Test
    fun observeOccurrence_withMasterIdReturnsMaster() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://master-occurrence"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val observed = repository.observeOccurrence(
            occurrenceId = OccurrenceId.Master(master.id),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(OccurrenceId.Master(master.id), observed?.occurrenceId)
        assertEquals(master.content.summary, observed?.title)
    }

    @Test
    fun observeOccurrence_carriesTheContactsOfTheAttendeesAndOfTheOrganizer() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://with-contacts"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master.withPeople(), "")))
        val alice = Contact(email = ALICE.email, name = "Alice", avatar = null, comesFromApi = true)
        val owner = Contact(email = OWNER.email, name = "Owner", avatar = null, comesFromApi = false)
        contactsLookup.contacts.value = listOf(alice, owner)

        val observed = repository.observeOccurrence(OccurrenceId.Master(master.id), TimeZone.UTC).first()

        assertEquals(listOf(alice), observed?.attendees?.map { it.contact })
        assertEquals(owner, observed?.organizer?.contact)
        assertEquals(listOf(ACCOUNT_ID), contactsLookup.preferredAccountIds, "the account of the event goes first")
    }

    @Test
    fun observeOccurrence_followsTheContacts() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://contacts-changing"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master.withPeople(), "")))
        val emissions = Channel<Event?>(Channel.UNLIMITED)
        backgroundScope.launch {
            repository.observeOccurrence(OccurrenceId.Master(master.id), TimeZone.UTC).collect(emissions::send)
        }

        assertNull(emissions.receive()?.attendees?.single()?.contact)
        val alice = Contact(email = ALICE.email, name = "Alice", avatar = null, comesFromApi = true)
        contactsLookup.contacts.value = listOf(alice)
        assertEquals(alice, emissions.receive()?.attendees?.single()?.contact)
    }

    @Test
    fun observeOccurrence_emitsNullWhenSeriesBecomesNonRecurring() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://series-to-plain"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val requested = OccurrenceId.Recurrence(
            masterId = master.id,
            recurrenceKey = RecurrenceKey.Utc(LocalDateTime(2026, 6, 17, 10, 0).toInstant(TimeZone.UTC)),
        )

        val emissions = mutableListOf<com.infomaniak.multiplatform_calendar.core.domain.model.event.Event?>()
        val job = backgroundScope.launch {
            repository.observeOccurrence(requested, TimeZone.UTC).collect { event ->
                emissions += event
                if (emissions.size == 1) {
                    eventDao().upsert(
                        listOf(
                            EventWithRawIcs(master.copy(rrule = null, hasRecurrence = false, recurrenceBounds = null), ""),
                        ),
                    )
                } else if (emissions.size == 2) {
                    cancel()
                }
            }
        }
        job.join()

        assertEquals(requested, emissions.first()?.occurrenceId)
        assertNull(emissions[1], "a stale recurrence id must resolve to null, never to the master")
    }

    @Test
    fun observeOccurrence_emitsNullWhenRuleNoLongerContainsOccurrence() = runTest {
        seedCalendar()

        val initial = dailyMasterEntity(EventId("event://rule-shrinks"), CALENDAR_ID).copy(
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 10),
        )
        eventDao().upsert(listOf(EventWithRawIcs(initial, "")))

        val requested = OccurrenceId.Recurrence(
            masterId = initial.id,
            recurrenceKey = RecurrenceKey.Utc(LocalDateTime(2026, 6, 22, 10, 0).toInstant(TimeZone.UTC)),
        )

        val emissions = mutableListOf<com.infomaniak.multiplatform_calendar.core.domain.model.event.Event?>()
        val job = backgroundScope.launch {
            repository.observeOccurrence(requested, TimeZone.UTC).collect { event ->
                emissions += event
                if (emissions.size == 1) {
                    val eventCopy = initial.copy(rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
                    eventDao().upsert(listOf(EventWithRawIcs(eventCopy, "")))
                } else if (emissions.size == 2) {
                    cancel()
                }
            }
        }
        job.join()

        assertEquals(requested, emissions.first()?.occurrenceId)
        assertNull(emissions[1], "when the slot disappears from RRULE, observeOccurrence must emit null")
    }

    @Test
    fun observeOccurrence_switchesFromGeneratedOccurrenceToOverride() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://override-add"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val keyStart = LocalDateTime(2026, 6, 17, 10, 0)
        val requested = OccurrenceId.Recurrence(master.id, RecurrenceKey.Utc(keyStart.toInstant(TimeZone.UTC)))

        val emissions = mutableListOf<com.infomaniak.multiplatform_calendar.core.domain.model.event.Event?>()
        val job = backgroundScope.launch {
            repository.observeOccurrence(requested, TimeZone.UTC).collect { event ->
                emissions += event
                if (emissions.size == 1) {
                    val override =
                        overrideEntity(master.id, originalStart = keyStart, movedTo = LocalDateTime(2026, 6, 17, 15, 0))
                    eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))
                } else if (emissions.size == 2) {
                    cancel()
                }
            }
        }
        job.join()

        assertEquals(
            zonedBounds(LocalDateTime(2026, 6, 17, 10, 0), LocalDateTime(2026, 6, 17, 11, 0), TimeZone.UTC),
            emissions[0]?.timing?.bounds,
        )
        assertEquals(
            zonedBounds(LocalDateTime(2026, 6, 17, 15, 0), LocalDateTime(2026, 6, 17, 16, 0), TimeZone.UTC),
            emissions[1]?.timing?.bounds,
        )
        assertEquals(requested, emissions[1]?.occurrenceId)
    }

    @Test
    fun observeOccurrence_emitsNullWhenOverrideBecomesCancelled() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://override-cancel"), CALENDAR_ID)
        val slot = LocalDateTime(2026, 6, 17, 10, 0)
        val initialOverride = overrideEntity(master.id, originalStart = slot)
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(initialOverride))))

        val requested = OccurrenceId.Recurrence(master.id, RecurrenceKey.Utc(slot.toInstant(TimeZone.UTC)))

        val emissions = mutableListOf<com.infomaniak.multiplatform_calendar.core.domain.model.event.Event?>()
        val job = backgroundScope.launch {
            repository.observeOccurrence(requested, TimeZone.UTC).collect { event ->
                emissions += event
                if (emissions.size == 1) {
                    val cancelled = overrideEntity(master.id, originalStart = slot, status = EventStatus.CANCELLED)
                    eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(cancelled))))
                } else if (emissions.size == 2) {
                    cancel()
                }
            }
        }
        job.join()

        assertEquals(requested, emissions[0]?.occurrenceId)
        assertNull(emissions[1])
    }

    @Test
    fun observeOccurrence_fallsBackToGeneratedOccurrenceWhenOverrideIsRemoved() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://override-removed"), CALENDAR_ID)
        val slot = LocalDateTime(2026, 6, 17, 10, 0)
        val initialOverride = overrideEntity(master.id, originalStart = slot, movedTo = LocalDateTime(2026, 6, 17, 15, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(initialOverride))))

        val requested = OccurrenceId.Recurrence(master.id, RecurrenceKey.Utc(slot.toInstant(TimeZone.UTC)))

        val emissions = mutableListOf<com.infomaniak.multiplatform_calendar.core.domain.model.event.Event?>()
        val job = backgroundScope.launch {
            repository.observeOccurrence(requested, TimeZone.UTC).collect { event ->
                emissions += event
                if (emissions.size == 1) {
                    eventDao().upsert(listOf(EventWithRawIcs(master, "", emptyList())))
                } else if (emissions.size == 2) {
                    cancel()
                }
            }
        }
        job.join()

        assertEquals(
            zonedBounds(LocalDateTime(2026, 6, 17, 15, 0), LocalDateTime(2026, 6, 17, 16, 0), TimeZone.UTC),
            emissions[0]?.timing?.bounds,
        )
        assertEquals(
            zonedBounds(LocalDateTime(2026, 6, 17, 10, 0), LocalDateTime(2026, 6, 17, 11, 0), TimeZone.UTC),
            emissions[1]?.timing?.bounds,
        )
        assertEquals(requested, emissions[1]?.occurrenceId)
    }

    @Test
    fun observeOccurrence_emitsNullWhenMasterIsDeleted() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://deleted-master"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val requested = OccurrenceId.Recurrence(
            master.id,
            RecurrenceKey.Utc(LocalDateTime(2026, 6, 17, 10, 0).toInstant(TimeZone.UTC)),
        )

        val emissions = mutableListOf<com.infomaniak.multiplatform_calendar.core.domain.model.event.Event?>()
        val job = backgroundScope.launch {
            repository.observeOccurrence(requested, TimeZone.UTC).collect { event ->
                emissions += event
                if (emissions.size == 1) {
                    eventDao().deleteEvent(master.id)
                } else if (emissions.size == 2) {
                    cancel()
                }
            }
        }
        job.join()

        assertEquals(requested, emissions[0]?.occurrenceId)
        assertNull(emissions[1])
    }

    @Test
    fun observeEvent_eventIdPathStillReturnsMasterAndThenNullOnDelete() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://legacy-event-id"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val emissions = mutableListOf<com.infomaniak.multiplatform_calendar.core.domain.model.event.Event?>()
        val job = backgroundScope.launch {
            repository.observeEvent(master.id).collect { event ->
                emissions += event
                if (emissions.size == 1) {
                    eventDao().deleteEvent(master.id)
                } else if (emissions.size == 2) {
                    cancel()
                }
            }
        }
        job.join()

        assertEquals(OccurrenceId.Master(master.id), emissions[0]?.occurrenceId)
        assertNull(emissions[1])
    }

    @Test
    fun observeVisibleDaySlices_expandsAllDayRecurringMasterIntoPerDayOccurrences() = runTest {
        seedCalendar()

        // All-day series through the whole read stack: padded bounds (via the real mapper) → range match →
        // all-day expansion anchored in UTC → per-day slicing. Distinct from the timed case above.
        val dtStart = LocalDateTime(2026, 6, 15, 0, 0)
        val dtEnd = LocalDateTime(2026, 6, 16, 0, 0)
        val timing = EventTimingEntity(
            dtStart = dtStart,
            dtEnd = dtEnd,
            dtEndEffective = dtEnd,
            startTimeZone = null,
            endTimeZone = null,
            dtStartInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            dtEndInstantMs = dtEnd.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            isAllDay = true,
        )
        val rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3)
        val master = EventEntity(
            id = EventId("event://all-day"),
            calendarId = CALENDAR_ID,
            content = EventContentEntity(
                summary = "All-day daily",
                timing = timing,
            ),
            rrule = rrule,
            recurrenceBounds = checkNotNull(
                toRecurrenceBoundsEntity(
                    timing = timing,
                    recurrenceRule = rrule,
                    rDates = emptyList(),
                ),
            ),
            etag = "1",
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val slicesByDay = repository.observeVisibleDaySlices(
            accountIds = setOf(ACCOUNT_ID),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        val occurrenceIds = slicesByDay.values.flatten().map { it.event.occurrenceId.value }
        assertEquals(3, occurrenceIds.size, "all-day DAILY COUNT=3 must yield 3 occurrences")
        assertEquals(occurrenceIds.toSet().size, occurrenceIds.size, "occurrence ids must be unique")
        assertTrue(occurrenceIds.all { it.endsWith("#event://all-day") }, "ids must name their master")
        assertEquals(3, slicesByDay.keys.size, "each all-day occurrence lands on its own day")
    }

    @Test
    fun observeVisibleEvents_findsTheUserAmongTheAttendeesByTheirAddressIgnoringCase() = runTest {
        seedCalendar()
        val me = ALICE.copy(email = "USER@example.com", status = ParticipationStatus.Tentative)

        val summary = observeSingleListedEvent(attendees = listOf(ALICE, me))

        assertTrue(summary.hasAttendees)
        assertEquals(ParticipationStatus.Tentative, summary.myStatus)
    }

    @Test
    fun observeVisibleEvents_findsTheUserAmongTheAttendeesByAnAlias() = runTest {
        seedCalendar()
        val me = ALICE.copy(email = "alias@example.com", status = ParticipationStatus.Declined)

        val summary = observeSingleListedEvent(attendees = listOf(ALICE, me))

        assertEquals(ParticipationStatus.Declined, summary.myStatus)
    }

    @Test
    fun observeVisibleEvents_hasNoStatusOfTheUserWhenTheyAreNotInvited() = runTest {
        seedCalendar()

        val summary = observeSingleListedEvent(attendees = listOf(ALICE))

        assertTrue(summary.hasAttendees)
        assertNull(summary.myStatus)
    }

    @Test
    fun observeVisibleEvents_hasNoAttendeesForAnEventWithoutGuests() = runTest {
        seedCalendar()

        val summary = observeSingleListedEvent(attendees = emptyList())

        assertFalse(summary.hasAttendees)
        assertNull(summary.myStatus)
    }

    @Test
    fun observeVisibleEvents_flagsTheMeetRoomAndTheBooking() = runTest {
        seedCalendar()

        val summary = observeSingleListedEvent { copy(meetRoomUrl = "https://kmeet.infomaniak.com/room", bookableUuid = "uuid") }

        assertTrue(summary.hasMeetRoom)
        assertTrue(summary.isBookable)
    }

    @Test
    fun observeVisibleEvents_flagsNothingOnAPlainEvent() = runTest {
        seedCalendar()

        val summary = observeSingleListedEvent { copy(meetRoomUrl = " ", bookableUuid = "") }

        assertFalse(summary.hasMeetRoom)
        assertFalse(summary.isBookable)
        assertFalse(summary.isRecurring)
    }

    @Test
    fun observeVisibleDaySlices_flagsEveryOccurrenceOfASeriesAsRecurringOverridesIncluded() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("event://daily-overridden"), CALENDAR_ID)
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 17, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val events = repository.observeVisibleDaySlices(
            accountIds = setOf(ACCOUNT_ID),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first().values.flatten().map { it.event }

        assertTrue(events.any { it.title == "Moved instance" })
        assertTrue(events.all { it.isRecurring })
    }

    private suspend fun observeSingleListedEvent(attendees: List<AttendeeEntity>): EventSummary =
        observeSingleListedEvent { copy(attendees = attendees) }

    private suspend fun observeSingleListedEvent(content: EventContentEntity.() -> EventContentEntity): EventSummary {
        val event = floatingEvent(CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(event.copy(content = event.content.content()), "")))

        return repository.observeVisibleEvents(
            accountIds = setOf(ACCOUNT_ID),
            start = LocalDateTime(2026, 6, 15, 10, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 15, 10, 30).toInstant(TimeZone.UTC),
            zone = TimeZone.UTC,
        ).first().single()
    }

    private fun EventEntity.withPeople() = copy(content = content.copy(attendees = listOf(ALICE), organizer = OWNER))

    private fun floatingEvent(calendarId: CalendarId): EventEntity {
        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        val dtEnd = LocalDateTime(2026, 6, 15, 11, 0)
        return EventEntity(
            id = EventId("event://floating"),
            calendarId = calendarId,
            content = EventContentEntity(
                summary = "Floating 10-11",
                timing = EventTimingEntity(
                    dtStart = dtStart,
                    dtEndEffective = dtEnd,
                    startTimeZone = null,
                    endTimeZone = null,
                    dtStartInstantMs = null,
                    dtEndInstantMs = null,
                ),
            ),
            etag = "1",
        )
    }
}
