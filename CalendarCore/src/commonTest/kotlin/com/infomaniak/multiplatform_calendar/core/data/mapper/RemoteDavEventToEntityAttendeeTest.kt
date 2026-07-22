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

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavAttendee
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavOrganizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteDavEventToEntityAttendeeTest {

    private val calendarId = CalendarId("https://cal/tests/")

    @Test
    fun organizer_isMappedToItsOwnColumn_separateFromAttendees() {
        val entity = remoteEvent(
            organizer = RemoteDavOrganizer(email = "boss@example.com", displayName = "Boss"),
            attendees = listOf(attendee("guest@example.com")),
        ).toEntity(calendarId)

        assertEquals("boss@example.com", entity.organizer?.email)
        assertEquals("Boss", entity.organizer?.displayName)
        assertEquals(listOf("guest@example.com"), entity.attendees.map { it.email })
    }

    @Test
    fun absentOrganizer_yieldsNullColumn() {
        val entity = remoteEvent(organizer = null, attendees = emptyList()).toEntity(calendarId)

        assertNull(entity.organizer)
    }

    @Test
    fun organizerAlsoListedAsAttendee_keepsBothEntriesDistinct() {
        val entity = remoteEvent(
            organizer = RemoteDavOrganizer(email = "boss@example.com", displayName = "Boss"),
            attendees = listOf(attendee("boss@example.com")),
        ).toEntity(calendarId)

        assertEquals("boss@example.com", entity.organizer?.email)
        assertEquals(1, entity.attendees.size)
        assertEquals("boss@example.com", entity.attendees.single().email)
    }

    private fun attendee(email: String) = RemoteDavAttendee(
        email = email,
        displayName = null,
        status = "ACCEPTED",
        role = "REQ-PARTICIPANT",
        responseNeeded = false,
    )

    private fun remoteEvent(
        organizer: RemoteDavOrganizer?,
        attendees: List<RemoteDavAttendee>,
    ) = RemoteDavEvent(
        url = "https://cal/tests/1.ics",
        etag = "etag-1",
        icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
        uid = "uid-1",
        summary = "Test",
        description = null,
        location = null,
        dtstart = "20260615T100000Z",
        dtStartTzid = null,
        dtend = "20260615T110000Z",
        dtEndTzid = null,
        duration = null,
        created = null,
        lastModified = null,
        dtstamp = null,
        rrule = null,
        status = null,
        transp = null,
        classification = null,
        priority = null,
        sequence = null,
        categories = null,
        colorHex = null,
        colorIcalName = null,
        attendees = attendees,
        organizer = organizer,
    )
}
