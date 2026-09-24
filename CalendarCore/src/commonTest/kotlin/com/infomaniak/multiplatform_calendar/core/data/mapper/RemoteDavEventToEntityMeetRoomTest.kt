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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteDavEventToEntityMeetRoomTest {

    private val calendarId = CalendarId("calendar://tests")

    @Test
    fun meetRoomUrl_isStoredInContentEntity() {
        val entity = remoteEvent(meetRoomUrl = "https://kmeet.infomaniak.com/lvqx-ozai-jeae-zdnd").toEntity(calendarId)

        assertEquals("https://kmeet.infomaniak.com/lvqx-ozai-jeae-zdnd", entity.content.meetRoomUrl)
    }

    @Test
    fun missingMeetRoomUrl_staysNull() {
        val entity = remoteEvent(meetRoomUrl = null).toEntity(calendarId)

        assertNull(entity.content.meetRoomUrl)
    }

    @Test
    fun bookableUuid_isStoredInContentEntity() {
        val entity = remoteEvent(bookableUuid = "bookable-id").toEntity(calendarId)

        assertEquals("bookable-id", entity.content.bookableUuid)
    }

    @Test
    fun missingBookableUuid_staysNull() {
        val entity = remoteEvent(bookableUuid = null).toEntity(calendarId)

        assertNull(entity.content.bookableUuid)
    }

    private fun remoteEvent(
        meetRoomUrl: String? = null,
        bookableUuid: String? = null,
    ) = RemoteDavEvent(
        url = "https://cal/tests/meet.ics",
        etag = "etag-1",
        icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
        uid = "uid-1",
        rrule = null,
        content = RemoteDavEventContent(
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
            status = null,
            transp = null,
            classification = null,
            priority = null,
            sequence = null,
            categories = null,
            meetRoomUrl = meetRoomUrl,
            bookableUuid = bookableUuid,
            colorHex = null,
            colorIcalName = null,
            attendees = emptyList(),
        ),
    )
}

