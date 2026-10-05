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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeRole
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceTarget
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Organizer
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ParticipationStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAttendeeEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAttendeesChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDateListChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteNameChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOrganizerChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOverrideRemoval
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

internal class EventRepositoryUpdateSeriesTest : EventRepositoryTestBase() {

    @Test
    fun updateEvent_thisOccurrence_detachesTheInstanceAddressedInTheMasterForm() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceScope.ThisOccurrence,
            ),
            // The app hands back the slot it displayed, the 16th, which the override must carry.
            data = editData(
                title = "Moved",
                start = LocalDateTime(2026, 6, 16, 10, 0),
                end = LocalDateTime(2026, 6, 16, 11, 0),
            ),
        )

        // No override claims that slot yet, so the master's own DTSTART form names it.
        val (recurrenceId, edit) = fakeCaldav.overrideUpserts.single()
        assertEquals("20260616T100000Z", recurrenceId.value)
        assertEquals("Moved", edit.summary)
        assertEquals("20260616T100000Z", edit.dtStart)
        assertEquals("20260616T110000Z", edit.dtEnd)
        assertEquals(emptyList(), fakeCaldav.patches, "the master itself must not be patched")
    }

    @Test
    fun updateEvent_thisOccurrence_addressesAnExistingOverrideUnderTheFormItWasWrittenIn() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0)).copy(
            recurrenceIdValue = "20260616T120000",
            recurrenceIdTzid = "Europe/Zurich",
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceScope.ThisOccurrence,
            ),
            data = editData(
                title = "Renamed",
                start = LocalDateTime(2026, 6, 16, 10, 0),
                end = LocalDateTime(2026, 6, 16, 11, 0),
            ),
        )

        // Re-deriving the master's form would detach a duplicate next to the override already there.
        val (recurrenceId, edit) = fakeCaldav.overrideUpserts.single()
        assertEquals("20260616T120000", recurrenceId.value)
        assertEquals("Europe/Zurich", recurrenceId.tzid)
        // Addressed by its RECURRENCE-ID, the instance still keeps the slot the edit gives it.
        assertEquals("20260616T100000Z", edit.dtStart)
        assertEquals("20260616T110000Z", edit.dtEnd)
    }

    @Test
    fun updateEvent_thisOccurrence_leavesTheRecurrenceSetToItsMaster() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceScope.ThisOccurrence,
            ),
            // The app hands back the series rule it displayed: an override must not adopt it.
            data = editData(
                title = "Renamed",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
            ),
        )

        val edit = fakeCaldav.overrideUpserts.single().second
        assertIs<RemoteRecurrenceChange.Unchanged>(edit.recurrenceChange)
        assertIs<RemoteDateListChange.Unchanged>(edit.exDateChange)
        assertIs<RemoteDateListChange.Unchanged>(edit.rDateChange)
        assertIs<RemoteOverrideRemoval.Unchanged>(edit.overrideRemoval)
    }

    @Test
    fun updateEvent_thisOccurrence_towardsAnotherCalendar_isRejected() = runTest {
        val otherCalendarId = CalendarId("calendar://other")
        seedCalendar()
        seedCalendar(calendarId = otherCalendarId)
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        // An override has no resource of its own, so there is nothing to move.
        assertFailsWith<IllegalArgumentException> {
            repository.updateEvent(
                credentials = CREDENTIALS,
                target = OccurrenceTarget.Recurring(
                    occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                    RecurrenceScope.ThisOccurrence,
                ),
                data = editData(title = "Moved away", calendarId = otherCalendarId),
            )
        }
        assertEquals(emptyList(), fakeCaldav.overrideUpserts)
    }

    @Test
    fun updateEvent_allOccurrences_patchesTheMasterItself() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            // The app hands back the slot it displayed, the 16th, not the series' own DTSTART.
            data = editData(
                title = "Renamed",
                start = LocalDateTime(2026, 6, 16, 10, 0),
                end = LocalDateTime(2026, 6, 16, 11, 0),
            ),
        )

        val edit = fakeCaldav.patches.single()
        assertEquals("Renamed", edit.summary)
        // Writing that slot verbatim would drag the series onto the 16th and lose its first instance.
        assertEquals("20260615T100000Z", edit.dtStart)
        assertEquals("20260615T110000Z", edit.dtEnd)
        assertEquals(emptyList(), fakeCaldav.overrideUpserts)
    }

    @Test
    fun updateEvent_allOccurrences_carriesTheChangeOntoDetachedOverrides() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        // That instance was detached earlier and moved to the 19th: it holds its fields in its own right.
        val override = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 16, 10, 0),
            movedTo = LocalDateTime(2026, 6, 19, 10, 0),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            data = editData(
                title = "Renamed",
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ),
        )

        assertEquals("Renamed", fakeCaldav.patches.single().summary)
        // iCalendar has no inheritance: the master's new title reaches the override only by being written.
        val (recurrenceId, edit) = fakeCaldav.overrideUpserts.single()
        assertEquals("Renamed", edit.summary)
        // The slot it answers to, and the one it was moved to, are its own and must survive the rename.
        assertEquals("20260616T100000Z", recurrenceId.value)
        assertEquals("20260619T100000Z", edit.dtStart)
        assertEquals("20260619T110000Z", edit.dtEnd)
    }

    @Test
    fun updateEvent_allOccurrences_leavesAnOverrideTheFieldsTheEditDidNotTouch() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val base = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        // A room only this instance was given, which the series knows nothing about.
        val override = base.copy(content = base.content.copy(location = "Room B"))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            data = editData(
                title = "Renamed",
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ),
        )

        // Replaying the whole of the edit would hand the override the master's empty location.
        val edit = fakeCaldav.overrideUpserts.single().second
        assertEquals("Renamed", edit.summary)
        assertEquals("Room B", edit.location)
    }

    @Test
    fun updateEvent_allOccurrences_carriesAnInvitedAttendeeOntoOverrides() = runTest {
        seedCalendar()
        val base = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val master = base.copy(content = base.content.copy(attendees = listOf(ALICE), organizer = OWNER))
        val baseOverride = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        val override = baseOverride.copy(
            content = baseOverride.content.copy(attendees = listOf(ALICE), organizer = OWNER),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            data = editData(
                title = "Daily recurring",
                start = LocalDateTime(2026, 6, 15, 10, 0),
                end = LocalDateTime(2026, 6, 15, 11, 0),
                attendees = listOf(ALICE.toEdit(), CAROL),
                organizer = Organizer(OWNER.email, OWNER.displayName),
            ),
        )

        val expected = RemoteAttendeesChange.Set(
            listOf(
                RemoteAttendeeEdit.Kept(ALICE.email),
                RemoteAttendeeEdit.Written(
                    CAROL.email,
                    displayName = RemoteNameChange.Unchanged,
                    role = "REQ-PARTICIPANT",
                    userType = null,
                ),
            ),
        )
        assertEquals(expected, fakeCaldav.patches.single().attendeesChange)
        val overrideEdit = fakeCaldav.overrideUpserts.single().second
        assertEquals(expected, overrideEdit.attendeesChange)
        assertEquals(RemoteOrganizerChange.Unchanged, overrideEdit.organizerChange)
    }

    @Test
    fun updateEvent_thisOccurrence_editsTheAttendeesAnExistingOverrideHas() = runTest {
        seedCalendar()
        val base = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val master = base.copy(content = base.content.copy(attendees = listOf(ALICE), organizer = OWNER))
        val baseOverride = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        // Only this instance invited Carol.
        val override = baseOverride.copy(
            content = baseOverride.content.copy(attendees = listOf(ALICE, CAROL_STORED), organizer = OWNER),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceScope.ThisOccurrence,
            ),
            data = editData(
                title = "Moved instance",
                start = LocalDateTime(2026, 6, 16, 10, 0),
                end = LocalDateTime(2026, 6, 16, 11, 0),
                attendees = listOf(CAROL),
                organizer = Organizer(OWNER.email, OWNER.displayName),
            ),
        )

        val edit = fakeCaldav.overrideUpserts.single().second
        assertEquals(RemoteAttendeesChange.Set(listOf(RemoteAttendeeEdit.Kept(CAROL.email))), edit.attendeesChange)
    }

    @Test
    fun updateEvent_allOccurrences_leavesOverridesAloneWhenOnlyTheTimingMoves() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            // Same title as the master's: only the hour moves.
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            data = editData(
                title = "Daily recurring",
                start = LocalDateTime(2026, 6, 17, 14, 0),
                end = LocalDateTime(2026, 6, 17, 15, 0),
            ),
        )

        // A detached instance keeps the slot it was moved to, so a series-wide shift is not its business.
        assertEquals(emptyList(), fakeCaldav.overrideUpserts)
    }

    @Test
    fun updateEvent_allOccurrences_leavesAnOverrideTheAlarmsItCannotState() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val base = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        // No trigger the domain can read, so no edit can ever state this alarm as a value.
        val override = base.copy(content = base.content.copy(alarms = listOf(AlarmEntity(action = "DISPLAY"))))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            data = editData(
                title = "Renamed",
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ),
        )

        // The rename reaches it, but replaying an alarm list it was never in would delete it.
        val edit = fakeCaldav.overrideUpserts.single().second
        assertEquals("Renamed", edit.summary)
        assertEquals(null, edit.alarms)
    }

    @Test
    fun updateEvent_allOccurrences_carriesAMovedTimeOverToTheWholeSeries() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            // The occurrence showed 10:00 and was pushed to 14:00: the series follows by that much.
            data = editData(
                title = "Event",
                start = LocalDateTime(2026, 6, 16, 14, 0),
                end = LocalDateTime(2026, 6, 16, 15, 30),
            ),
        )

        val edit = fakeCaldav.patches.single()
        assertEquals("20260615T140000Z", edit.dtStart)
        // The edited duration travels with it rather than the one the master used to have.
        assertEquals("20260615T153000Z", edit.dtEnd)
    }

    @Test
    fun updateEvent_allOccurrences_fromAnOverriddenOccurrence_measuresTheShiftFromWhatItShowed() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        // That instance was already moved to the 19th at 10:00, and that is what the user saw.
        val override = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 16, 10, 0),
            movedTo = LocalDateTime(2026, 6, 19, 10, 0),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceScope.AllOccurrences,
            ),
            data = editData(
                title = "Event",
                start = LocalDateTime(2026, 6, 19, 11, 0),
                end = LocalDateTime(2026, 6, 19, 12, 0),
            ),
        )

        // Measured against the override's own start, the edit is one hour: not the three days and
        // one hour that its original slot would have suggested.
        assertEquals("20260615T110000Z", fakeCaldav.patches.single().dtStart)
    }

    @Test
    fun updateEvent_allOccurrences_fromAnOverriddenOccurrence_leavesTheSeriesWhatOnlyThatOccurrenceHad() = runTest {
        val master = seedSeriesWithDistinctOverrides()
        val occurrenceId = occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0))
        val shown = checkNotNull(repository.getEditData(occurrenceId))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceId, RecurrenceScope.AllOccurrences),
            data = shown.copy(description = "Agenda"),
        )

        // The title, room and guests the edit opened on are that occurrence's own, not the series'.
        val masterEdit = fakeCaldav.patches.single()
        assertEquals("Daily recurring", masterEdit.summary)
        assertEquals(null, masterEdit.location)
        assertEquals("Agenda", masterEdit.description)
        assertEquals(RemoteAttendeesChange.Unchanged, masterEdit.attendeesChange)
        assertEquals(RemoteOrganizerChange.Unchanged, masterEdit.organizerChange)
        val other = fakeCaldav.overrideUpserts.single { (recurrenceId, _) -> recurrenceId.value == "20260617T100000Z" }.second
        assertEquals("Other instance", other.summary)
        assertEquals("Room C", other.location)
        assertEquals("Agenda", other.description)
        assertEquals(RemoteAttendeesChange.Unchanged, other.attendeesChange)
    }

    @Test
    fun updateEvent_allOccurrences_fromAnOverriddenOccurrence_carriesWhatTheEditChanged() = runTest {
        val master = seedSeriesWithDistinctOverrides()
        val occurrenceId = occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0))
        val shown = checkNotNull(repository.getEditData(occurrenceId))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceId, RecurrenceScope.AllOccurrences),
            data = shown.copy(title = "Renamed", attendees = listOf(CAROL)),
        )

        val expectedAttendees = RemoteAttendeesChange.Set(
            listOf(
                RemoteAttendeeEdit.Written(
                    CAROL.email,
                    displayName = RemoteNameChange.Unchanged,
                    role = "REQ-PARTICIPANT",
                    userType = null,
                ),
            ),
        )
        val masterEdit = fakeCaldav.patches.single()
        assertEquals("Renamed", masterEdit.summary)
        assertEquals(null, masterEdit.location)
        assertEquals(expectedAttendees, masterEdit.attendeesChange)
        val overrideEdits = fakeCaldav.overrideUpserts.associate { (recurrenceId, edit) -> recurrenceId.value to edit }
        assertEquals(setOf("20260616T100000Z", "20260617T100000Z"), overrideEdits.keys)
        overrideEdits.values.forEach { assertEquals("Renamed", it.summary) }
        assertEquals("Room B", overrideEdits.getValue("20260616T100000Z").location)
        assertEquals("Room C", overrideEdits.getValue("20260617T100000Z").location)
        assertEquals(expectedAttendees, overrideEdits.getValue("20260617T100000Z").attendeesChange)
    }

    @Test
    fun updateEvent_allOccurrences_fromAnOverriddenOccurrence_givesInvitedAttendeesTheirOrganizer() = runTest {
        seedCalendar()
        // Only the 16th has participants: neither the master nor the 17th has an organizer.
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val shownBase = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        val shown = shownBase.copy(content = shownBase.content.copy(attendees = listOf(ALICE), organizer = OWNER))
        val other = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 17, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(shown, other))))
        val occurrenceId = occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 10, 0))
        val edit = checkNotNull(repository.getEditData(occurrenceId))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceId, RecurrenceScope.AllOccurrences),
            data = edit.copy(attendees = edit.attendees + CAROL),
        )

        // Unchanged on screen, the organizer still has to come along with the attendees it answers for.
        val expected = RemoteOrganizerChange.Set(OWNER.email, OWNER.displayName)
        assertEquals(expected, fakeCaldav.patches.single().organizerChange)
        val otherEdit = fakeCaldav.overrideUpserts.single { (recurrenceId, _) -> recurrenceId.value == "20260617T100000Z" }
        assertEquals(expected, otherEdit.second.organizerChange)
    }

    /** A daily series whose 16th and 17th are detached, each with fields the master does not have. */
    private suspend fun seedSeriesWithDistinctOverrides(): EventEntity {
        seedCalendar()
        val base = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val master = base.copy(content = base.content.copy(attendees = listOf(ALICE), organizer = OWNER))
        val shownBase = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        val shown = shownBase.copy(
            content = shownBase.content.copy(
                location = "Room B",
                attendees = listOf(ALICE, CAROL_STORED),
                organizer = OWNER,
            ),
        )
        val otherBase = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 17, 10, 0))
        val other = otherBase.copy(
            content = otherBase.content.copy(
                summary = "Other instance",
                location = "Room C",
                attendees = listOf(ALICE),
                organizer = OWNER,
            ),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(shown, other))))

        return master
    }

    private companion object {
        val CAROL = AttendeeEdit(email = "carol@example.com", displayName = null, role = AttendeeRole.Requested)
        val CAROL_STORED = AttendeeEntity(
            email = CAROL.email,
            status = ParticipationStatus.NeedsAction,
            role = CAROL.role,
        )
    }
}
