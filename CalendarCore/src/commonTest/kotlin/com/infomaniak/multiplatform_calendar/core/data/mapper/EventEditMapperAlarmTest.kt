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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmTrigger
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class EventEditMapperAlarmTest {

    private val calendarId = CalendarId("calendar://alarms")

    @Test
    fun sameAlarmsAsPrevious_emitsUnchanged() {
        val previous = eventEntity(alarms = listOf(alarmEntity(triggerRelative = (-15).minutes)))
        val same = eventAlarm(offset = -15.minutes)

        val edit = editData(alarms = listOf(same)).toRemoteEdit(stamp = STAMP, previous = previous)

        assertNull(edit.alarms)
    }

    @Test
    fun differentAlarmCount_emitsSet() {
        val previous = eventEntity(alarms = listOf(alarmEntity(triggerRelative = (-15).minutes)))

        val edit = editData(alarms = emptyList()).toRemoteEdit(stamp = STAMP, previous = previous)

        assertEquals(0, assertNotNull(edit.alarms).size)
    }

    @Test
    fun modifiedTrigger_emitsSetWithRebuiltList() {
        val previous = eventEntity(alarms = listOf(alarmEntity(triggerRelative = (-15).minutes)))

        val edit = editData(alarms = listOf(eventAlarm(offset = -5.minutes)))
            .toRemoteEdit(stamp = STAMP, previous = previous)

        val set = assertNotNull(edit.alarms)
        assertEquals(1, set.size)
        assertEquals("-PT5M", set.single().triggerDuration)
    }

    @Test
    fun modifiedDescriptionOnly_emitsSet() {
        val previous = eventEntity(
            alarms = listOf(
                alarmEntity(triggerRelative = (-15).minutes, description = "Old"),
            ),
        )

        val edit = editData(alarms = listOf(eventAlarm(offset = -15.minutes, description = "New")))
            .toRemoteEdit(stamp = STAMP, previous = previous)

        val set = assertNotNull(edit.alarms)
        assertEquals("New", set.single().description)
    }

    @Test
    fun noPreviousEntity_andEmptyAlarms_emitsUnchanged() {
        val edit = editData(alarms = emptyList()).toRemoteEdit(stamp = STAMP, previous = null)

        assertNull(edit.alarms)
    }

    @Test
    fun addedAlarm_toEventWithoutPrevious_emitsSet() {
        val edit = editData(alarms = listOf(eventAlarm(offset = -5.minutes)))
            .toRemoteEdit(stamp = STAMP, previous = null)

        assertNotNull(edit.alarms)
    }

    @Test
    fun absoluteTrigger_isFormattedAsUtcDateTime() {
        val absolute = EventAlarm(
            action = AlarmAction.Display,
            trigger = AlarmTrigger.Absolute(Instant.fromEpochMilliseconds(1_781_514_000_000L)),
            description = "R",
        )

        val edit = editData(alarms = listOf(absolute)).toRemoteEdit(stamp = STAMP, previous = null)

        val emitted = assertNotNull(edit.alarms).single()
        assertEquals("20260615T090000Z", emitted.triggerAbsolute)
    }

    private fun eventAlarm(offset: Duration, description: String? = "Reminder") = EventAlarm(
        action = AlarmAction.Display,
        trigger = AlarmTrigger.Relative(offset = offset, relatedTo = TriggerRelation.Start),
        description = description,
    )

    private fun alarmEntity(
        triggerRelative: Duration? = null,
        triggerAbsolute: Instant? = null,
        description: String? = "Reminder",
    ) = AlarmEntity(
        action = "DISPLAY",
        triggerRelative = triggerRelative,
        triggerAbsolute = triggerAbsolute,
        triggerRelatedTo = TriggerRelation.Start,
        description = description,
    )

    private fun editData(alarms: List<EventAlarm>) = EventEditData(
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
        calendarId = calendarId,
        eventColor = null,
        alarms = alarms,
    )

    private fun eventEntity(alarms: List<AlarmEntity>) = EventEntity(
        id = EventId("https://cal/tests/1.ics"),
        calendarId = calendarId,
        summary = "Test",
        timing = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
            dtStartInstantMs = null,
            dtEndInstantMs = null,
        ),
        etag = "etag-1",
        alarms = alarms,
    )

    private companion object {
        const val STAMP = "20260615T090000Z"
    }
}
