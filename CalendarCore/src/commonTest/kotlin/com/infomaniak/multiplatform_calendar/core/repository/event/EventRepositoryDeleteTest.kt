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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceTarget
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDateListChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOverrideRemoval
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

internal class EventRepositoryDeleteTest : EventRepositoryTestBase() {

    @Test
    fun deleteEvent_thisOccurrence_excludesTheDateAndDropsItsOverride() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0), movedTo = LocalDateTime(2026, 6, 19, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)), RecurrenceScope.ThisOccurrence),
        )

        val edit = fakeCaldav.patches.single()
        val exDates = assertIs<RemoteDateListChange.Set>(edit.exDateChange)
        assertEquals(listOf("20260616T100000Z"), exDates.lines.flatMap { it.values })
        // The override redefining that instance goes in the same patch, or it would outlive its slot.
        val removal = assertIs<RemoteOverrideRemoval.Instances>(edit.overrideRemoval)
        assertEquals(listOf("20260616T100000Z"), removal.recurrenceIds.map { it.value })
        assertEquals(emptyList(), fakeCaldav.deletes, "the resource itself must survive")
    }

    @Test
    fun deleteEvent_thisOccurrence_dropsTheOverrideUnderTheFormItWasWrittenIn() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        // The server wrote that RECURRENCE-ID in a zone of its own: the same instant, another spelling.
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0)).copy(
            recurrenceIdValue = "20260616T120000",
            recurrenceIdTzid = "Europe/Zurich",
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)), RecurrenceScope.ThisOccurrence),
        )

        // Addressing it in the master's form would match no VEVENT and leave the override behind.
        val removal = assertIs<RemoteOverrideRemoval.Instances>(fakeCaldav.patches.single().overrideRemoval)
        assertEquals(listOf("20260616T120000"), removal.recurrenceIds.map { it.value })
        assertEquals(listOf("Europe/Zurich"), removal.recurrenceIds.map { it.tzid })
    }

    @Test
    fun deleteEvent_thisOccurrence_keepsTheRestOfTheSeriesUntouched() = runTest {
        seedCalendar()
        val rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3)
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = rrule,
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)), RecurrenceScope.ThisOccurrence),
        )

        // Excluding a date must read as an edit that changes nothing else: the rule above all, which a
        // rebuilt edit would otherwise clear.
        val edit = fakeCaldav.patches.single()
        assertEquals(RemoteRecurrenceChange.Unchanged, edit.recurrenceChange)
        assertEquals("Daily recurring", edit.summary)
        // The removal is asked for even with no override on record: another client may have written one
        // this device has not synced yet, and dropping nothing is free.
        val removal = assertIs<RemoteOverrideRemoval.Instances>(edit.overrideRemoval)
        assertEquals(listOf("20260616T100000Z"), removal.recurrenceIds.map { it.value })
    }

    @Test
    fun deleteEvent_allOccurrences_deletesTheWholeResource() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)), RecurrenceScope.AllOccurrences),
        )

        assertEquals(listOf(master.id.url to "1"), fakeCaldav.deletes)
        assertEquals(emptyList(), fakeCaldav.patches)
        assertNull(database.eventDao().getEvent(master.id))
    }

    @Test
    fun deleteEvent_onAMasterId_deletesTheWholeResourceWhateverTheScope() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        // A master designates no instance, so there is nothing to exclude: only the series as a whole.
        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(OccurrenceId.Master(master.id), RecurrenceScope.ThisOccurrence),
        )

        assertEquals(listOf(master.id.url to "1"), fakeCaldav.deletes)
    }

    @Test
    fun deleteEvent_thisAndFollowing_boundsACountedRuleOnThePrecedingInstance() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 18, 10, 0)), RecurrenceScope.ThisAndFollowing),
        )

        // A counted rule stays counted: the 4th instance is the pivot, so 3 survive.
        val change = assertIs<RemoteRecurrenceChange.Set>(fakeCaldav.patches.single().recurrenceChange)
        assertEquals("FREQ=DAILY;COUNT=3", change.value)
        assertEquals(emptyList(), fakeCaldav.deletes, "the resource itself must survive")
    }

    @Test
    fun deleteEvent_thisAndFollowing_leavesTheSeriesAloneOnASlotItNeverHad() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            // 10:30 on a series that only ever runs at 10:00.
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 18, 10, 30)), RecurrenceScope.ThisAndFollowing),
        )

        assertEquals(emptyList(), fakeCaldav.patches, "a pivot the rule never generated must bound nothing")
        assertEquals(emptyList(), fakeCaldav.deletes, "the resource itself must survive")
    }

    @Test
    fun deleteEvent_thisOccurrence_leavesTheSeriesAloneOnAnAlreadyExcludedSlot() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily),
        ).copy(exDates = listOf(icalUtc(2026, 6, 18)))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 18, 10, 0)), RecurrenceScope.ThisOccurrence),
        )

        assertEquals(emptyList(), fakeCaldav.patches, "what the series no longer hands out cannot be dropped again")
    }

    @Test
    fun deleteEvent_thisAndFollowing_boundsASeriesTooDenseToMaterialise() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Minutely),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            // Past the 100_000 instances an expansion may hand back, which a rank has no reason to obey.
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 9, 15, 10, 0)), RecurrenceScope.ThisAndFollowing),
        )

        val change = assertIs<RemoteRecurrenceChange.Set>(fakeCaldav.patches.single().recurrenceChange)
        assertEquals("FREQ=MINUTELY;UNTIL=20260915T095900Z", change.value)
    }

    @Test
    fun deleteEvent_thisAndFollowing_boundsAnEndlessRuleWithUntil() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 18, 10, 0)), RecurrenceScope.ThisAndFollowing),
        )

        // UNTIL is inclusive, so it lands on the last instance kept, not on the pivot.
        val change = assertIs<RemoteRecurrenceChange.Set>(fakeCaldav.patches.single().recurrenceChange)
        assertEquals("FREQ=DAILY;UNTIL=20260617T100000Z", change.value)
    }

    @Test
    fun deleteEvent_thisAndFollowing_dropsWhatTheCutTailCarried() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 10),
        ).copy(
            exDates = listOf(icalUtc(2026, 6, 16), icalUtc(2026, 6, 19)),
            rDates = listOf(icalUtc(2026, 6, 17), icalUtc(2026, 6, 20)),
        )
        val overrides = listOf(
            overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0)),
            overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 19, 10, 0)),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", overrides)))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 18, 10, 0)), RecurrenceScope.ThisAndFollowing),
        )

        // Only what applied to a surviving instance is kept; the rest would outlive its target.
        val edit = fakeCaldav.patches.single()
        assertEquals(listOf("20260616T100000Z"), assertIs<RemoteDateListChange.Set>(edit.exDateChange).lines.flatMap { it.values })
        assertEquals(listOf("20260617T100000Z"), assertIs<RemoteDateListChange.Set>(edit.rDateChange).lines.flatMap { it.values })
        val removal = assertIs<RemoteOverrideRemoval.Instances>(edit.overrideRemoval)
        assertEquals(listOf("20260619T100000Z"), removal.recurrenceIds.map { it.value })
    }

    @Test
    fun deleteEvent_thisAndFollowing_onTheFirstInstance_deletesTheWholeResource() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        // Cutting at DTSTART leaves no instance, and no rule can express an empty series.
        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 15, 10, 0)), RecurrenceScope.ThisAndFollowing),
        )

        assertEquals(listOf(master.id.url to "1"), fakeCaldav.deletes)
        assertEquals(emptyList(), fakeCaldav.patches)
    }

    @Test
    fun deleteEvent_thisAndFollowing_onTheFirstInstanceOfARDateOnlySeries_deletesTheWholeResource() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rDates = listOf(icalUtc(2026, 6, 17), icalUtc(2026, 6, 20)),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        // DTSTART is an instance of a series carried by RDATE alone, so cutting there leaves nothing.
        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 15, 10, 0)), RecurrenceScope.ThisAndFollowing),
        )

        assertEquals(listOf(master.id.url to "1"), fakeCaldav.deletes)
        assertEquals(emptyList(), fakeCaldav.patches, "no rule to bound must not mean nothing to delete")
    }

    @Test
    fun deleteEvent_thisAndFollowing_onARDateOnlySeries_keepsWhatPrecedesThePivot() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rDates = listOf(icalUtc(2026, 6, 17), icalUtc(2026, 6, 20)),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 20, 10, 0)), RecurrenceScope.ThisAndFollowing),
        )

        // DTSTART and the earlier RDATE still stand, so the resource does too.
        val edit = fakeCaldav.patches.single()
        assertEquals(listOf("20260617T100000Z"), assertIs<RemoteDateListChange.Set>(edit.rDateChange).lines.flatMap { it.values })
        assertEquals(emptyList(), fakeCaldav.deletes)
    }

    @Test
    fun deleteEvent_thisOccurrence_leavesTheSeriesAlarmsAlone() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        ).let { it.copy(content = it.content.copy(alarms = listOf(AlarmEntity(action = "DISPLAY")))) }
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        repository.deleteEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)), RecurrenceScope.ThisOccurrence),
        )

        // That alarm carries no trigger the domain can read: rebuilding the list would delete it.
        assertNull(fakeCaldav.patches.single().alarms, "excluding a date must not touch the alarms")
    }
}
