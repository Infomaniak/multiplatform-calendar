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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventWithOverrides
import com.infomaniak.multiplatform_calendar.core.domain.model.event.expandRecurrencesInWindow
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.extensions.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.utils.ColorComputation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class EventRecurrenceStateTest {

    @BeforeTest
    fun setUp() {
        ColorComputation.resetCache()
    }

    @Test
    fun eventWithoutRecurrence_isNone() {
        val event = eventEntity().toDomain(calendar)

        assertEquals(EventRecurrenceState.None, event.recurrence)
    }

    @Test
    fun eventWithRule_isMaster() {
        val event = eventEntity(rrule = RecurrenceRule(freq = Frequency.Daily)).toDomain(calendar)

        assertEquals(EventRecurrenceState.Master, event.recurrence)
    }

    @Test
    fun eventWithRDatesButNoRule_isMaster() {
        val event = eventEntity(rDates = listOf(rDate)).toDomain(calendar)

        assertEquals(EventRecurrenceState.Master, event.recurrence)
    }

    @Test
    fun eventWithExDatesOnly_isNone() {
        // EXDATE only subtracts from a set it cannot create, so it never makes an event a series.
        val event = eventEntity(exDates = listOf(rDate)).toDomain(calendar)

        assertEquals(EventRecurrenceState.None, event.recurrence)
    }

    @Test
    fun override_isOccurrence() {
        // Non-regression: an override carries no rule of its own, which used to make it report as a
        // plain event and let an action on one occurrence pass for an action on a standalone event.
        val override = overrideEntity().toDomain(calendar)

        assertEquals(EventRecurrenceState.Occurrence, override.recurrence)
    }

    @Test
    fun expandedOccurrence_isOccurrence() = runTest {
        // An occurrence keeps the rule it was generated from, so only its identity tells it apart.
        val master = eventEntity(rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3)).toDomain(calendar)

        val occurrences = expand(master)

        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { it.recurrence == EventRecurrenceState.Occurrence })
    }

    @Test
    fun rDateOccurrence_isOccurrence() = runTest {
        val master = eventEntity(rDates = listOf(rDate)).toDomain(calendar)

        val occurrences = expand(master)

        // A rule-less series still holds its own DTSTART alongside the listed date (RFC 5545 §3.8.5.2),
        // and neither of the two is the master any more.
        assertEquals(
            listOf(LocalDateTime(2026, 6, 15, 10, 0), LocalDateTime(2026, 6, 20, 10, 0)),
            occurrences.map { it.timing.start },
        )
        assertTrue(occurrences.all { it.recurrence == EventRecurrenceState.Occurrence })
    }

    @Test
    fun master_staysMasterWhileItsOccurrencesDoNot() {
        // The same event answers differently depending on how it was read, and that is the point:
        // reading a series by id yields its master, reading a range yields its occurrences.
        val master = eventEntity(rrule = RecurrenceRule(freq = Frequency.Daily)).toDomain(calendar)

        assertEquals(EventRecurrenceState.Master, master.recurrence)
    }

    private suspend fun expand(master: Event): List<Event> = listOf(EventWithOverrides(master)).expandRecurrencesInWindow(
        rangeStart = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
        rangeEnd = LocalDateTime(2026, 6, 25, 0, 0).toInstant(TimeZone.UTC),
        timeZone = TimeZone.UTC,
    )

    private val masterId = EventId("https://cal/tests/1.ics")
    private val calendarId = CalendarId("calendar://tests")
    private val calendar = Calendar(
        id = calendarId,
        accountId = AccountId(1L),
        displayName = "Tests",
        colors = CalendarColors.from(0xFF0000FF.toInt()),
        isVisible = true,
    )
    private val start = LocalDateTime(2026, 6, 15, 10, 0)
    private val end = LocalDateTime(2026, 6, 15, 11, 0)
    private val rDate = IcalDateValue.Zoned(LocalDateTime(2026, 6, 20, 10, 0).toInstant(TimeZone.UTC), TimeZone.UTC.id)

    private fun eventEntity(
        rrule: RecurrenceRule? = null,
        rDates: List<IcalDateValue> = emptyList(),
        exDates: List<IcalDateValue> = emptyList(),
    ) = EventEntity(
        id = masterId,
        calendarId = calendarId,
        content = contentEntity(start, end),
        etag = "etag-1",
        rrule = rrule,
        rDates = rDates,
        exDates = exDates,
    )

    private fun overrideEntity() = EventOverrideEntity(
        masterId = masterId,
        recurrenceKey = RecurrenceKey.Utc(start.toInstant(TimeZone.UTC)),
        recurrenceIdValue = start.toInstant(TimeZone.UTC).toICalUtcDateTime(),
        recurrenceIdTzid = null,
        originalStartInstantMs = start.toInstant(TimeZone.UTC).toEpochMilliseconds(),
        originalEndInstantMs = end.toInstant(TimeZone.UTC).toEpochMilliseconds(),
        originalStartLocalDateTime = start,
        originalEndLocalDateTime = end,
        content = contentEntity(start, end),
    )

    private fun contentEntity(start: LocalDateTime, end: LocalDateTime) = EventContentEntity(
        summary = "Test",
        timing = EventTimingEntity(
            dtStart = start,
            dtEndEffective = end,
            startTimeZone = TimeZone.UTC.id,
            endTimeZone = TimeZone.UTC.id,
            dtStartInstantMs = start.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            dtEndInstantMs = end.toInstant(TimeZone.UTC).toEpochMilliseconds(),
        ),
    )
}
