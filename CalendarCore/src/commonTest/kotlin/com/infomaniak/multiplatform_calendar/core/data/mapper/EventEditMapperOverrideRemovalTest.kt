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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOverrideRemoval
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Dropping an override travels inside the master's patch, so a deletion never leaves the series with
 * an occurrence its rule no longer generates.
 */
class EventEditMapperOverrideRemovalTest {

    @Test
    fun anEmptyListLeavesEveryOverrideInPlace() {
        val edit = editData().toRemoteEdit(stamp = STAMP, previous = null)

        assertEquals(RemoteOverrideRemoval.Unchanged, edit.overrideRemoval)
    }

    @Test
    fun droppedKeysAreExpressedAgainstTheMastersDtstart() {
        val edit = editData().toRemoteEdit(
            stamp = STAMP,
            previous = null,
            droppedOverrides = listOf(
                RecurrenceKey.Floating(LocalDateTime(2026, 6, 16, 10, 0)),
                RecurrenceKey.Floating(LocalDateTime(2026, 6, 17, 10, 0)),
            ),
        )

        val removal = assertIs<RemoteOverrideRemoval.Instances>(edit.overrideRemoval)
        assertEquals(listOf("20260616T100000", "20260617T100000"), removal.recurrenceIds.map { it.value })
    }

    @Test
    fun aKeyDesignatingNoOccurrenceIsLeftOut() {
        val edit = editData().toRemoteEdit(
            stamp = STAMP,
            previous = null,
            // Zoned against a floating master: it can match no override either.
            droppedOverrides = listOf(RecurrenceKey.Zoned(LocalDateTime(2026, 6, 16, 10, 0), "Europe/Paris")),
        )

        val removal = assertIs<RemoteOverrideRemoval.Instances>(edit.overrideRemoval)
        assertEquals(emptyList(), removal.recurrenceIds)
    }

    @Test
    fun aRecordedOverrideIsAddressedWithTheVeryRecurrenceIdItCarries() {
        val master = EventTiming(
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 11, 0),
            startTimeZone = TimeZone.of("Europe/Zurich"),
            endTimeZone = TimeZone.of("Europe/Zurich"),
            isAllDay = false,
        )
        val slot = RecurrenceKey.Zoned(LocalDateTime(2026, 6, 16, 10, 0), "Europe/Zurich")

        val edit = editData().copy(timing = master).toRemoteEdit(
            stamp = STAMP,
            previous = null,
            droppedOverrides = listOf(slot),
            // Another client wrote that override in UTC, which the master's form would not match.
            knownOverrides = listOf(recordedOverride(slot, value = "20260616T080000Z", tzid = null)),
        )

        val removal = assertIs<RemoteOverrideRemoval.Instances>(edit.overrideRemoval)
        assertEquals(listOf("20260616T080000Z"), removal.recurrenceIds.map { it.value })
        assertEquals(listOf(null), removal.recurrenceIds.map { it.tzid })
    }

    @Test
    fun anUnrecordedKeyFallsBackOnTheMastersForm() {
        val edit = editData().toRemoteEdit(
            stamp = STAMP,
            previous = null,
            droppedOverrides = listOf(RecurrenceKey.Floating(LocalDateTime(2026, 6, 16, 10, 0))),
            knownOverrides = listOf(
                recordedOverride(RecurrenceKey.Floating(LocalDateTime(2026, 6, 17, 10, 0)), value = "20260617T100000", tzid = null),
            ),
        )

        val removal = assertIs<RemoteOverrideRemoval.Instances>(edit.overrideRemoval)
        assertEquals(listOf("20260616T100000"), removal.recurrenceIds.map { it.value })
    }

    private fun recordedOverride(slot: RecurrenceKey, value: String, tzid: String?) = EventOverrideEntity(
        masterId = EventId("https://cal/main/series.ics"),
        recurrenceKey = slot,
        recurrenceIdValue = value,
        recurrenceIdTzid = tzid,
        originalStartInstantMs = null,
        originalEndInstantMs = null,
        originalStartLocalDateTime = LocalDateTime(2026, 6, 16, 10, 0),
        originalEndLocalDateTime = LocalDateTime(2026, 6, 16, 11, 0),
        content = EventContentEntity(
            summary = "Override",
            timing = EventTimingEntity(
                dtStart = LocalDateTime(2026, 6, 16, 10, 0),
                dtEndEffective = LocalDateTime(2026, 6, 16, 11, 0),
                startTimeZone = null,
                endTimeZone = null,
                dtStartInstantMs = null,
                dtEndInstantMs = null,
            ),
        ),
    )

    private fun editData() = EventEditData(
        title = "Test",
        timing = EventTiming(
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 11, 0),
            startTimeZone = null,
            endTimeZone = null,
            isAllDay = false,
        ),
        location = null,
        description = null,
        timeBlocking = null,
        calendarId = CalendarId("calendar://tests"),
        eventColor = null,
        alarms = emptyList(),
    )
}

private const val STAMP = "20260615T090000Z"
