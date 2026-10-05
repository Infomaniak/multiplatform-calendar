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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.local.entity.RecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.data.repository.EventRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSourceColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceTarget
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteColorChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

internal class EventRepositoryEditDataTest : EventRepositoryTestBase() {

    /**
     * The point of the whole method: an edit read back and handed over untouched must be a no-op. A
     * colour and a set of alarms rebuilt from the domain [Event] would both be written back here.
     */
    @Test
    fun getEditData_handedBackUntouched_writesNeitherColourNorAlarms() = runTest {
        // The calendar is coloured, the event is not: reading the event's colour resolved against its
        // calendar would hand the calendar's colour back as if the event owned it.
        seedCalendar(color = CalendarSourceColor(0xFF8E24AA.toInt()))
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
            // No trigger the domain can read: projecting the alarms would drop this one.
        ).let { it.copy(content = it.content.copy(alarms = listOf(AlarmEntity(action = "DISPLAY")))) }
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        val data = assertNotNull(repository.getEditData(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0))))
        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)), RecurrenceScope.AllOccurrences),
            data = data,
        )

        val edit = fakeCaldav.patches.single()
        assertEquals(RemoteColorChange.Unchanged, edit.colorChange, "the calendar's colour is not the event's own")
        assertNull(edit.alarms, "an untouched edit must leave the resource's own alarms alone")
        // Not `Set`: the rule read back is equal to the stored one, so nothing is rewritten at all.
        // Had it been lost on the way, this would read `Cleared`.
        assertEquals(RemoteRecurrenceChange.Unchanged, edit.recurrenceChange, "the rule the edit never touched")
    }

    /**
     * An occurrence is displayed on its own slot, and that is what [EventRepository.updateEvent] expects
     * an edit prepared on it to carry — while the rule stays the master's, so a whole-series edit does
     * not drop the recurrence it never touched.
     */
    @Test
    fun getEditData_carriesTheOccurrenceSlotAndTheMastersRule() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("https://cal/main/daily.ics"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))

        val data = assertNotNull(repository.getEditData(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0))))

        assertEquals(LocalDateTime(2026, 6, 17, 10, 0), data.timing.start, "the slot the occurrence is shown on")
        assertEquals(LocalDateTime(2026, 6, 17, 11, 0), data.timing.end, "the master's duration, carried over")
        assertEquals(master.rrule, data.timing.recurrenceRule, "the rule lives on the master")
        assertEquals(AlarmListEdit.Preserve, data.alarms)
    }

    /**
     * A master whose interval straddles a fall-back lasts four hours on the clock face but five in real
     * time, and the expansion shows the occurrences with the latter. Deriving the edit's end on the face
     * would open it an hour short of the slot the user is looking at, and shorten the series on save.
     */
    @Test
    fun getEditData_endsTheOccurrenceWhereTheExpansionShowsIt() = runTest {
        seedCalendar()
        val master = dstStraddlingMasterEntity(EventId("https://cal/main/dst.ics"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))
        val occurrence = parisOccurrenceOf(master.id, LocalDateTime(2025, 11, 2, 1, 0))

        val data = assertNotNull(repository.getEditData(occurrence))

        assertEquals(LocalDateTime(2025, 11, 2, 1, 0), data.timing.start, "the slot the occurrence is shown on")
        assertEquals(LocalDateTime(2025, 11, 2, 6, 0), data.timing.end, "the five hours the expansion shows")
    }

    /** The counterpart of [getEditData_endsTheOccurrenceWhereTheExpansionShowsIt]: a no-op save moves nothing. */
    @Test
    fun getEditData_onADstStraddlingSeries_handedBackUntouched_leavesTheMastersEndAlone() = runTest {
        seedCalendar()
        val master = dstStraddlingMasterEntity(EventId("https://cal/main/dst.ics"), CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT")))
        val occurrence = parisOccurrenceOf(master.id, LocalDateTime(2025, 11, 2, 1, 0))

        val data = assertNotNull(repository.getEditData(occurrence))
        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrence, RecurrenceScope.AllOccurrences),
            data = data,
        )

        val edit = fakeCaldav.patches.single()
        assertEquals("20251026T010000", edit.dtStart, "the master keeps its own start")
        assertEquals("20251026T050000", edit.dtEnd, "and its own end: four hours on the face, where it was written")
    }

    /** An overridden instance is redefined by its own VEVENT: the edit must describe it, not its slot. */
    @Test
    fun getEditData_readsAnOverriddenInstanceFromItsOverride() = runTest {
        seedCalendar()
        val master = dailyMasterEntity(EventId("https://cal/main/overridden.ics"), CALENDAR_ID)
        val override = overrideEntity(
            masterId = master.id,
            originalStart = LocalDateTime(2026, 6, 17, 10, 0),
            movedTo = LocalDateTime(2026, 6, 17, 15, 0),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", overrides = listOf(override))))

        val data = assertNotNull(repository.getEditData(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0))))

        assertEquals("Moved instance", data.title, "the override redefines the instance")
        assertEquals(LocalDateTime(2026, 6, 17, 15, 0), data.timing.start, "where the override moved it")
        assertEquals(master.rrule, data.timing.recurrenceRule, "an override carries no rule: the master's stands")
    }

    @Test
    fun getEditData_returnsNullWhenTheEventIsGone() = runTest {
        assertNull(repository.getEditData(OccurrenceId.Master(EventId("https://cal/main/unknown.ics"))))
    }

    private fun parisOccurrenceOf(masterId: EventId, start: LocalDateTime) = OccurrenceId.Recurrence(
        masterId = masterId,
        recurrenceKey = RecurrenceKey.Zoned(start, PARIS.id),
    )

    /** Weekly, 01:00-05:00 on the day Paris falls back: four hours on the face, five of real time. */
    private fun dstStraddlingMasterEntity(eventId: EventId, calendarId: CalendarId): EventEntity {
        val dtStart = LocalDateTime(2025, 10, 26, 1, 0)
        val dtEnd = LocalDateTime(2025, 10, 26, 5, 0)
        return EventEntity(
            id = eventId,
            calendarId = calendarId,
            content = EventContentEntity(
                summary = "Long night",
                timing = EventTimingEntity(
                    dtStart = dtStart,
                    dtEndEffective = dtEnd,
                    startTimeZone = PARIS.id,
                    endTimeZone = PARIS.id,
                    dtStartInstantMs = dtStart.toInstant(PARIS).toEpochMilliseconds(),
                    dtEndInstantMs = dtEnd.toInstant(PARIS).toEpochMilliseconds(),
                ),
            ),
            rrule = RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 4),
            hasRecurrence = true,
            recurrenceBounds = RecurrenceBoundsEntity(
                firstOccurrenceInstantMs = dtStart.toInstant(PARIS).toEpochMilliseconds(),
                recurrenceBoundKind = RecurrenceBoundKind.FiniteDeferred,
            ),
            etag = "1",
        )
    }
}
