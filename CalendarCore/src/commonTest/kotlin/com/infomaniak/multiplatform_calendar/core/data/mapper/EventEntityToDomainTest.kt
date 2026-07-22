/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.OrganizerEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeRole
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventSourceColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ParticipationStatus
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventEntityToDomainTest {

    @Test
    fun blankDescriptionAndLocation_areNormalizedToNull() {
        val event = eventEntity(description = "   ", location = "").toDomain(calendar)

        assertNull(event.description)
        assertNull(event.location)
    }

    @Test
    fun nonBlankDescriptionAndLocation_arePreserved() {
        val event = eventEntity(description = "Some notes", location = "Paris").toDomain(calendar)

        assertEquals("Some notes", event.description)
        assertEquals("Paris", event.location)
    }

    @Test
    fun absentDescriptionAndLocation_stayNull() {
        val event = eventEntity(description = null, location = null).toDomain(calendar)

        assertNull(event.description)
        assertNull(event.location)
    }

    @Test
    fun blankCategoryEntries_areDropped() {
        val event = eventEntity(categories = listOf("work", "  ", "", "personal")).toDomain(calendar)

        assertEquals(listOf("work", "personal"), event.categories)
    }

    @Test
    fun categoriesLeftEmptyAfterFiltering_becomeEmptyList() {
        val event = eventEntity(categories = listOf("  ", "")).toDomain(calendar)

        assertEquals(emptyList(), event.categories)
    }

    // ---- Colors ---------------------------------------------------------------------------------

    @Test
    fun eventWithoutColorArgb_inheritsCalendarColors() {
        val event = eventEntity(colorArgb = null).toDomain(calendar)

        assertNull(event.colors.eventSourceColor)
        assertEquals(calendar.colors.datavizContainer, event.colors.datavizContainer)
        assertEquals(calendar.colors.onDatavizContainer, event.colors.onDatavizContainer)
        assertEquals(calendar.colors.datavizContainerVariant, event.colors.datavizContainerVariant)
        assertEquals(calendar.colors.onDatavizContainerVariant, event.colors.onDatavizContainerVariant)
    }

    @Test
    fun eventWithColorArgb_exposesItAsEventSourceColor() {
        val eventColor = 0xFFE53935.toInt()

        val event = eventEntity(colorArgb = eventColor).toDomain(calendar)

        assertEquals(EventSourceColor(eventColor), event.colors.eventSourceColor)
        assertNotNull(event.colors.datavizContainer)
    }

    @Test
    fun eventsSharingColorArgb_reuseSameCachedEventColors() {
        val shared = 0xFF1E88E5.toInt()
        val cache = mutableMapOf<EventSourceColor, EventColors>()

        val a = eventEntity(id = "a", colorArgb = shared).toDomain(calendar, cache)
        val b = eventEntity(id = "b", colorArgb = shared).toDomain(calendar, cache)

        assertSame(a.colors, b.colors)
        assertEquals(1, cache.size)
    }

    @Test
    fun eventsWithDistinctColorArgb_populateSeparateCacheEntries() {
        val cache = mutableMapOf<EventSourceColor, EventColors>()

        eventEntity(id = "a", colorArgb = 0xFF1E88E5.toInt()).toDomain(calendar, cache)
        eventEntity(id = "b", colorArgb = 0xFFE53935.toInt()).toDomain(calendar, cache)

        assertEquals(2, cache.size)
    }

    // ---- Organizer / attendees ------------------------------------------------------------------

    @Test
    fun organizerEntity_isMappedToDomainOrganizer() {
        val event = eventEntity(
            organizer = OrganizerEntity(email = "boss@example.com", displayName = "Boss"),
        ).toDomain(calendar)

        val organizer = event.organizer
        assertNotNull(organizer)
        assertEquals("boss@example.com", organizer.email)
        assertEquals("Boss", organizer.displayName)
    }

    @Test
    fun absentOrganizer_isNull() {
        val event = eventEntity(organizer = null).toDomain(calendar)

        assertNull(event.organizer)
    }

    @Test
    fun organizerThatIsNotAnAttendee_isExposedWithoutPollutingAttendees() {
        val event = eventEntity(
            organizer = OrganizerEntity(email = "boss@example.com", displayName = "Boss"),
            attendees = emptyList(),
        ).toDomain(calendar)

        assertEquals("boss@example.com", event.organizer?.email)
        assertEquals(emptyList(), event.attendees)
    }

    @Test
    fun organizerThatIsAlsoAnAttendee_isNotDuplicatedIntoAttendees() {
        val event = eventEntity(
            organizer = OrganizerEntity(email = "boss@example.com", displayName = "Boss"),
            attendees = listOf(
                AttendeeEntity(
                    email = "boss@example.com",
                    displayName = "Boss",
                    status = ParticipationStatus.Accepted,
                    role = AttendeeRole.Chair,
                ),
            ),
        ).toDomain(calendar)

        assertEquals("boss@example.com", event.organizer?.email)
        assertEquals(1, event.attendees.size)
        assertEquals(ParticipationStatus.Accepted, event.attendees.single().status)
        assertTrue(event.attendees.single().isOrganizer)
    }

    @Test
    fun attendeeThatIsNotTheOrganizer_isNotFlaggedAsOrganizer() {
        val event = eventEntity(
            organizer = OrganizerEntity(email = "boss@example.com", displayName = "Boss"),
            attendees = listOf(
                AttendeeEntity(
                    email = "guest@example.com",
                    status = ParticipationStatus.NeedsAction,
                    role = AttendeeRole.Requested,
                ),
            ),
        ).toDomain(calendar)

        assertFalse(event.attendees.single().isOrganizer)
    }

    private val calendarId = CalendarId("calendar://tests")
    private val accountId = AccountId(1L)
    private val calendar = Calendar(
        id = calendarId,
        accountId = accountId,
        displayName = "Tests",
        colors = CalendarColors.from(0xFF0000FF.toInt()),
        isVisible = true,
    )

    private fun eventEntity(
        id: String = "https://cal/tests/1.ics",
        description: String? = null,
        location: String? = null,
        categories: List<String>? = null,
        colorArgb: Int? = null,
        attendees: List<AttendeeEntity> = emptyList(),
        organizer: OrganizerEntity? = null,
    ) = EventEntity(
        id = EventId(id),
        calendarId = calendarId,
        summary = "Test",
        description = description,
        location = location,
        timing = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
            dtStartInstantMs = null,
            dtEndInstantMs = null,
        ),
        categories = categories,
        attendees = attendees,
        organizer = organizer,
        colorArgb = colorArgb,
        etag = "etag-1",
    )
}
