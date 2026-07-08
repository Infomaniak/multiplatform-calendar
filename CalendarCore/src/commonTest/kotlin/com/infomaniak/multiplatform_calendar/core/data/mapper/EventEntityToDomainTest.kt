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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventEntityToDomainTest {

    @Test
    fun blankDescriptionAndLocation_areNormalizedToNull() {
        val event = eventEntity(description = "   ", location = "").toDomain(calendar, eventColors)

        assertNull(event.description)
        assertNull(event.location)
    }

    @Test
    fun nonBlankDescriptionAndLocation_arePreserved() {
        val event = eventEntity(description = "Some notes", location = "Paris").toDomain(calendar, eventColors)

        assertEquals("Some notes", event.description)
        assertEquals("Paris", event.location)
    }

    @Test
    fun absentDescriptionAndLocation_stayNull() {
        val event = eventEntity(description = null, location = null).toDomain(calendar, eventColors)

        assertNull(event.description)
        assertNull(event.location)
    }

    @Test
    fun blankCategoryEntries_areDropped() {
        val event = eventEntity(categories = listOf("work", "  ", "", "personal")).toDomain(calendar, eventColors)

        assertEquals(listOf("work", "personal"), event.categories)
    }

    @Test
    fun categoriesLeftEmptyAfterFiltering_becomeNull() {
        val event = eventEntity(categories = listOf("  ", "")).toDomain(calendar, eventColors)

        assertNull(event.categories)
    }

    private val calendarId = CalendarId("calendar://tests")
    private val accountId = AccountId(1L)
    private val calendar = Calendar(
        id = calendarId,
        accountId = accountId,
        displayName = "Tests",
        color = CalendarColor(0xFF0000FF.toInt()),
        isVisible = true,
    )
    private val eventColors = EventColors.from(0xFF0000FF.toInt())

    private fun eventEntity(
        description: String? = null,
        location: String? = null,
        categories: List<String>? = null,
    ) = EventEntity(
        id = EventId("https://cal/tests/1.ics"),
        calendarId = calendarId,
        summary = "Test",
        description = description,
        location = location,
        dtStart = LocalDateTime(2026, 6, 15, 10, 0),
        dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
        dtStartInstantMs = null,
        dtEndInstantMs = null,
        categories = categories,
        etag = "etag-1",
        rawIcs = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
    )
}
