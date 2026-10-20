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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmRepetitionEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmTrigger
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import com.infomaniak.multiplatform_calendar.core.domain.model.event.zonedTiming
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAlarmRepetition
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
        val stored = alarmEntity(triggerRelative = (-15).minutes)

        val edit = editData(alarms = listOf(stored.read())).toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

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
        val stored = alarmEntity(triggerRelative = (-15).minutes)

        val edit = editData(alarms = listOf(stored.read().copy(trigger = relative(-5.minutes))))
            .toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

        assertEquals("-PT5M", assertNotNull(edit.alarms).single().triggerDuration)
    }

    @Test
    fun modifiedTrigger_keepsTheHiddenPropertiesOfTheStoredAlarm() {
        val stored = alarmEntity(
            triggerRelative = (-15).minutes,
            action = "EMAIL",
            description = "Body",
            summary = "Subject",
            attendees = listOf("mailto:a@example.com"),
            attachments = listOf("https://example.com/a.txt"),
            repetition = AlarmRepetitionEntity(2, 5.minutes),
        )

        val edit = editData(alarms = listOf(stored.read().copy(trigger = relative(-5.minutes))))
            .toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

        val emitted = assertNotNull(edit.alarms).single()
        assertEquals("-PT5M", emitted.triggerDuration)
        assertEquals("Body", emitted.description)
        assertEquals("Subject", emitted.summary)
        assertEquals(listOf("mailto:a@example.com"), emitted.attendees)
        assertEquals(listOf("https://example.com/a.txt"), emitted.attach)
        assertEquals(RemoteAlarmRepetition(count = 2, interval = "PT5M"), emitted.repetition)
    }

    @Test
    fun modifiedAction_keepsTheHiddenPropertiesOfTheStoredAlarm() {
        val stored = alarmEntity(triggerRelative = (-15).minutes, description = "Kept", uid = "server-uid")

        val edit = editData(alarms = listOf(stored.read().copy(action = AlarmAction.Audio)))
            .toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

        val emitted = assertNotNull(edit.alarms).single()
        assertEquals("AUDIO", emitted.action)
        assertEquals("Kept", emitted.description)
        assertEquals("server-uid", emitted.uid)
    }

    @Test
    fun alarmsFiringAlike_eachTakeBackTheirOwnHiddenProperties() {
        val stored = listOf(
            alarmEntity(triggerRelative = (-15).minutes, description = "First"),
            alarmEntity(triggerRelative = (-15).minutes, description = "Second"),
        )
        val (first, second) = stored.toDomain()

        val edit = editData(alarms = listOf(first, second.copy(trigger = relative(-5.minutes))))
            .toRemoteEdit(stamp = STAMP, previous = eventEntity(stored))

        assertEquals(listOf("First", "Second"), assertNotNull(edit.alarms).map { it.description })
    }

    @Test
    fun alarmsFiringAlike_keepingOnlyTheSecond_keepsItsOwnHiddenProperties() {
        val stored = listOf(
            alarmEntity(triggerRelative = (-15).minutes, action = "EMAIL", attendees = listOf("mailto:first@example.com")),
            alarmEntity(triggerRelative = (-15).minutes, action = "EMAIL", attendees = listOf("mailto:second@example.com")),
        )
        val second = stored.toDomain()[1]

        val edit = editData(alarms = listOf(second.copy(trigger = relative(-5.minutes))))
            .toRemoteEdit(stamp = STAMP, previous = eventEntity(stored))

        assertEquals(listOf("mailto:second@example.com"), assertNotNull(edit.alarms).single().attendees)
    }

    @Test
    fun alarmsFiringAlike_reordered_eachKeepTheirOwnHiddenProperties() {
        val stored = listOf(
            alarmEntity(triggerRelative = (-15).minutes, description = "First"),
            alarmEntity(triggerRelative = (-15).minutes, description = "Second"),
        )
        val (first, second) = stored.toDomain()

        val edit = editData(alarms = listOf(second, first)).toRemoteEdit(stamp = STAMP, previous = eventEntity(stored))

        assertEquals(listOf("Second", "First"), assertNotNull(edit.alarms).map { it.description })
    }

    @Test
    fun noPreviousEntity_andEmptyAlarms_emitsUnchanged() {
        val edit = editData(alarms = emptyList()).toRemoteEdit(stamp = STAMP, previous = null)

        assertNull(edit.alarms)
    }

    @Test
    fun absoluteTrigger_isFormattedAsUtcDateTime() {
        val absolute = EventAlarm(
            action = AlarmAction.Display,
            trigger = AlarmTrigger.Absolute(Instant.fromEpochMilliseconds(1_781_514_000_000L)),
        )

        val edit = editData(alarms = listOf(absolute)).toRemoteEdit(stamp = STAMP, previous = null)

        val emitted = assertNotNull(edit.alarms).single()
        assertEquals("20260615T090000Z", emitted.triggerAbsolute)
        assertEquals("START", emitted.triggerRelatedTo)
    }

    @Test
    fun serverUid_onAnUntouchedAlarm_isNotSeenAsAChange() {
        val stored = alarmEntity(triggerRelative = (-15).minutes, uid = "server-uid")

        val edit = editData(alarms = listOf(stored.read())).toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

        assertNull(edit.alarms)
    }

    @Test
    fun serverUid_isWrittenBack_whenTheAlarmListIsRebuilt() {
        val stored = alarmEntity(triggerRelative = (-15).minutes, uid = "server-uid")

        val edit = editData(alarms = listOf(stored.read().copy(trigger = relative(-5.minutes))))
            .toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

        assertEquals("server-uid", assertNotNull(edit.alarms).single().uid)
    }

    @Test
    fun anAlarmTheServerNeverNamed_goesOutWithoutAUid() {
        val stored = alarmEntity(triggerRelative = (-15).minutes)

        val edit = editData(alarms = listOf(stored.read().copy(trigger = relative(-5.minutes))))
            .toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

        assertNull(assertNotNull(edit.alarms).single().uid)
    }

    @Test
    fun hiddenRepetition_onAnUntouchedAlarm_isNotSeenAsAChange() {
        val stored = alarmEntity(triggerRelative = (-15).minutes, repetition = AlarmRepetitionEntity(2, 5.minutes))

        val edit = editData(alarms = listOf(stored.read())).toRemoteEdit(stamp = STAMP, previous = eventEntity(listOf(stored)))

        assertNull(edit.alarms)
    }

    @Test
    fun storedUnknownActionAlarm_isLeftOutOfTheComparison() {
        val known = alarmEntity(triggerRelative = (-15).minutes)
        val previous = eventEntity(listOf(known, alarmEntity(triggerRelative = (-30).minutes, action = "X-CUSTOM")))

        val edit = editData(alarms = listOf(known.read())).toRemoteEdit(stamp = STAMP, previous = previous)

        assertNull(edit.alarms)
    }

    @Test
    fun storedUnknownActionAlarm_isNotRestatedWhenTheListIsRebuilt() {
        val previous = eventEntity(
            alarms = listOf(
                alarmEntity(triggerRelative = (-15).minutes),
                alarmEntity(triggerRelative = (-30).minutes, action = "X-CUSTOM"),
            ),
        )

        val edit = editData(alarms = listOf(newAlarm(AlarmAction.Display))).toRemoteEdit(stamp = STAMP, previous = previous)

        assertEquals(listOf("DISPLAY"), assertNotNull(edit.alarms).map { it.action })
    }

    @Test
    fun statedUnknownActionAlarm_isIgnored() {
        val stated = listOf(newAlarm(AlarmAction.Display), newAlarm(AlarmAction.Unknown("X-CUSTOM")))

        val edit = editData(alarms = stated).toRemoteEdit(stamp = STAMP, previous = null)

        assertEquals(listOf("DISPLAY"), assertNotNull(edit.alarms).map { it.action })
    }

    private fun AlarmEntity.read() = listOf(this).toDomain().single()

    private fun relative(offset: Duration) = AlarmTrigger.Relative(offset = offset, relatedTo = TriggerRelation.Start)

    private fun newAlarm(action: AlarmAction) = EventAlarm(action = action, trigger = relative(-5.minutes))

    private fun alarmEntity(
        triggerRelative: Duration? = null,
        description: String? = "Reminder",
        summary: String? = null,
        attendees: List<String> = emptyList(),
        attachments: List<String> = emptyList(),
        uid: String? = null,
        repetition: AlarmRepetitionEntity? = null,
        action: String = "DISPLAY",
    ) = AlarmEntity(
        uid = uid,
        action = action,
        triggerRelative = triggerRelative,
        triggerRelatedTo = TriggerRelation.Start,
        description = description,
        summary = summary,
        attendees = attendees,
        attachments = attachments,
        repetition = repetition,
    )

    private fun editData(alarms: List<EventAlarm>) = EventEditData(
        title = "Test",
        timing = zonedTiming(LocalDateTime(2026, 6, 15, 10, 0), LocalDateTime(2026, 6, 15, 11, 0), TimeZone.UTC),
        location = null,
        description = null,
        timeBlocking = null,
        calendarId = calendarId,
        eventColor = null,
        alarms = AlarmListEdit.Replace(alarms),
        attendees = emptyList(),
        organizer = null,
        meetRoom = null,
    )

    private fun eventEntity(alarms: List<AlarmEntity>) = EventEntity(
        id = EventId("https://cal/tests/1.ics"),
        calendarId = calendarId,
        content = EventContentEntity(
            summary = "Test",
            timing = EventTimingEntity(
                dtStart = LocalDateTime(2026, 6, 15, 10, 0),
                dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
                dtStartInstantMs = null,
                dtEndInstantMs = null,
            ),
            alarms = alarms,
        ),
        etag = "etag-1",
    )

    private companion object {
        const val STAMP = "20260615T090000Z"
    }
}
