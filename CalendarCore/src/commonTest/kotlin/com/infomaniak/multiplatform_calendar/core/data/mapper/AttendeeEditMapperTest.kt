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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.OrganizerEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeRole
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeType
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Organizer
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ParticipationStatus
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAttendeeEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAttendeesChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteNameChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOrganizerChange
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttendeeEditMapperTest {

    private val organizer = Organizer(email = "owner@example.com", displayName = "Owner")
    private val storedOrganizer = OrganizerEntity(email = "owner@example.com", displayName = "Owner")
    private val alice = AttendeeEntity(
        email = "alice@example.com",
        displayName = "Alice",
        status = ParticipationStatus.Accepted,
        role = AttendeeRole.Requested,
    )
    private val bob = AttendeeEntity(
        email = "bob@example.com",
        status = ParticipationStatus.NeedsAction,
        role = AttendeeRole.Optional,
    )

    @Test
    fun sameAttendees_emitsUnchanged() {
        val data = editData(attendees = listOf(alice.toEdit(), bob.toEdit()))

        assertEquals(RemoteAttendeesChange.Unchanged, data.resolveAttendeesChange(listOf(alice, bob)))
    }

    @Test
    fun storedAttendeesAreKept_editedAndNewOnesWritten() {
        val renamedBob = bob.toEdit().copy(displayName = "Bob")
        val carol = AttendeeEdit(email = "carol@example.com", displayName = null, role = AttendeeRole.Chair)

        val change = editData(attendees = listOf(alice.toEdit(), renamedBob, carol))
            .resolveAttendeesChange(listOf(alice, bob))

        val expected = listOf(
            RemoteAttendeeEdit.Kept("alice@example.com"),
            RemoteAttendeeEdit.Written("bob@example.com", RemoteNameChange.Set("Bob"), role = null, userType = null),
            RemoteAttendeeEdit.Written("carol@example.com", RemoteNameChange.Unchanged, "CHAIR", userType = null),
        )
        assertEquals(RemoteAttendeesChange.Set(expected), change)
    }

    @Test
    fun editedAttendee_writesOnlyWhatChanged() {
        val edited = listOf(
            alice.toEdit().copy(displayName = null),
            bob.toEdit().copy(role = AttendeeRole.Chair, type = AttendeeType.Group),
        )

        val change = editData(attendees = edited).resolveAttendeesChange(listOf(alice, bob))

        val expected = listOf(
            RemoteAttendeeEdit.Written("alice@example.com", RemoteNameChange.Cleared, role = null, userType = null),
            RemoteAttendeeEdit.Written("bob@example.com", RemoteNameChange.Unchanged, "CHAIR", "GROUP"),
        )
        assertEquals(RemoteAttendeesChange.Set(expected), change)
    }

    @Test
    fun newRoom_isWrittenWithItsTypeAndAsNonParticipant() {
        val room = AttendeeEdit(email = "room@example.com", displayName = "Jules Verne", type = AttendeeType.Room)

        val change = editData(attendees = listOf(room)).resolveAttendeesChange(emptyList())

        val expected = RemoteAttendeeEdit.Written(
            "room@example.com",
            RemoteNameChange.Set("Jules Verne"),
            role = "NON-PARTICIPANT",
            userType = "ROOM",
        )
        assertEquals(RemoteAttendeesChange.Set(listOf(expected)), change)
    }

    @Test
    fun removedAttendee_isLeftOutOfTheSetList() {
        val change = editData(attendees = listOf(bob.toEdit())).resolveAttendeesChange(listOf(alice, bob))

        assertEquals(RemoteAttendeesChange.Set(listOf(RemoteAttendeeEdit.Kept("bob@example.com"))), change)
    }

    @Test
    fun attendeesStatedTwice_areWrittenOnce() {
        val shouting = alice.toEdit().copy(email = "ALICE@example.com")

        val change = editData(attendees = listOf(alice.toEdit(), shouting)).resolveAttendeesChange(emptyList())

        val expected = RemoteAttendeeEdit.Written("alice@example.com", RemoteNameChange.Set("Alice"), "REQ-PARTICIPANT", userType = null)
        assertEquals(RemoteAttendeesChange.Set(listOf(expected)), change)
    }

    @Test
    fun attendeesWithoutOrganizer_areRejected() {
        val data = editData(attendees = listOf(alice.toEdit()), organizer = null)

        assertFailsWith<IllegalArgumentException> { data.resolveAttendeesChange(emptyList()) }
    }

    @Test
    fun storedAttendeesWithoutOrganizer_areTolerated() {
        val data = editData(attendees = listOf(alice.toEdit()), organizer = null)

        assertEquals(RemoteAttendeesChange.Unchanged, data.resolveAttendeesChange(listOf(alice)))
        assertEquals(RemoteOrganizerChange.Unchanged, data.resolveOrganizerChange(previous = null))
    }

    @Test
    fun sameOrganizer_emitsUnchanged() {
        assertEquals(RemoteOrganizerChange.Unchanged, editData().resolveOrganizerChange(storedOrganizer))
    }

    @Test
    fun otherOrganizer_emitsSet() {
        val data = editData(organizer = Organizer(email = "new@example.com"))

        assertEquals(RemoteOrganizerChange.Set("new@example.com", null), data.resolveOrganizerChange(storedOrganizer))
    }

    @Test
    fun droppedOrganizer_emitsCleared() {
        assertEquals(RemoteOrganizerChange.Cleared, editData(organizer = null).resolveOrganizerChange(storedOrganizer))
    }

    @Test
    fun droppedOrganizerWhileKeepingAttendees_isRejected() {
        val data = editData(attendees = listOf(alice.toEdit()), organizer = null)

        assertFailsWith<IllegalArgumentException> { data.resolveOrganizerChange(storedOrganizer) }
    }

    private fun editData(
        attendees: List<AttendeeEdit> = emptyList(),
        organizer: Organizer? = this.organizer,
    ) = EventEditData(
        title = "Test",
        timing = EventTiming(
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 11, 0),
            startTimeZone = TimeZone.UTC,
            endTimeZone = TimeZone.UTC,
            isAllDay = false,
        ),
        location = null,
        description = null,
        timeBlocking = null,
        calendarId = CalendarId("calendar://attendees"),
        eventColor = null,
        alarms = AlarmListEdit.Replace(emptyList()),
        attendees = attendees,
        organizer = organizer,
    )
}
