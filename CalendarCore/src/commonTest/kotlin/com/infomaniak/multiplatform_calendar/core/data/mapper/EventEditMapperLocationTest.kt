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

import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Location
import com.infomaniak.multiplatform_calendar.core.domain.model.event.zonedTiming
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventEditMapperLocationTest {

    private val calendarId = CalendarId("calendar://location")

    @Test
    fun place_writesItsTextAndDropsTheRoom() {
        val previous = eventEntity(location = "Old place", bookableUuid = "room-uuid")

        val edit = editData(Location.Place("Paris")).toRemoteEdit(stamp = STAMP, previous = previous)

        assertEquals("Paris", edit.location)
        assertNull(edit.bookableUuid)
    }

    @Test
    fun blankPlace_clearsTheText() {
        val edit = editData(Location.Place("  ")).toRemoteEdit(stamp = STAMP, previous = eventEntity(location = "Paris"))

        assertNull(edit.location)
    }

    @Test
    fun room_writesItsUuidAndKeepsTheText() {
        val previous = eventEntity(location = "Paris", bookableUuid = null)

        val edit = editData(Location.BookableRoom(BookableResourceUuid("room-uuid"), resource = null))
            .toRemoteEdit(stamp = STAMP, previous = previous)

        assertEquals("Paris", edit.location)
        assertEquals("room-uuid", edit.bookableUuid)
    }

    @Test
    fun noLocation_clearsTheTextAndTheRoom() {
        val previous = eventEntity(location = "Paris", bookableUuid = "room-uuid")

        val edit = editData(location = null).toRemoteEdit(stamp = STAMP, previous = previous)

        assertNull(edit.location)
        assertNull(edit.bookableUuid)
    }

    @Test
    fun overrideRoom_keepsTheTextTheInstanceShows() {
        val edit = editData(Location.BookableRoom(BookableResourceUuid("room-uuid"), resource = null)).toOverrideEdit(
            stamp = STAMP,
            previousColorArgb = null,
            previousDescription = null,
            previousMeetRoomUrl = null,
            previousLocation = "Paris",
            previousAlarms = emptyList(),
            previousAttendees = emptyList(),
            previousOrganizer = null,
            previousClassification = Classification.Public,
        )

        assertEquals("Paris", edit.location)
        assertEquals("room-uuid", edit.bookableUuid)
    }

    private fun editData(location: Location?) = EventEditData(
        title = "Test",
        timing = zonedTiming(
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 11, 0),
            zone = TimeZone.UTC,
        ),
        location = location,
        description = null,
        timeBlocking = null,
        classification = Classification.Public,
        calendarId = calendarId,
        eventColor = null,
        alarms = AlarmListEdit.Preserve,
        attendees = emptyList(),
        organizer = null,
        meetRoom = null,
    )

    private fun eventEntity(location: String?, bookableUuid: String? = null) = EventEntity(
        id = EventId("https://cal/tests/1.ics"),
        calendarId = calendarId,
        content = EventContentEntity(
            summary = "Test",
            timing = EventTimingEntity(
                dtStart = LocalDateTime(2026, 6, 15, 10, 0),
                dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
                startTimeZone = TimeZone.UTC.id,
                endTimeZone = TimeZone.UTC.id,
                dtStartInstantMs = null,
                dtEndInstantMs = null,
            ),
            location = location,
            bookableUuid = bookableUuid,
        ),
        etag = "etag-1",
    )

    private companion object {
        const val STAMP = "20260615T090000Z"
    }
}
