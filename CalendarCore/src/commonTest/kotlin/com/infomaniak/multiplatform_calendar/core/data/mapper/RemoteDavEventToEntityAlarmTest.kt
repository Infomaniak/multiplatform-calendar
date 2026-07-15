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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavAlarm
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteDavEventToEntityAlarmTest {

    private val calendarId = CalendarId("calendar://alarms")

    @Test
    fun eventWithNoAlarms_producesEmptyList() {
        val entity = remoteEvent(alarms = emptyList()).toEntity(calendarId)

        assertTrue(entity.alarms.isEmpty())
    }

    @Test
    fun relativeTrigger_isParsedToNegativeMillis_andRelatedToDefaultsToStart() {
        val remote = alarm(triggerDuration = "-PT15M", triggerRelatedTo = "START")
        val entity = remoteEvent(alarms = listOf(remote)).toEntity(calendarId).alarms.single()

        assertEquals(-15L * 60_000L, entity.triggerRelativeMillis)
        assertNull(entity.triggerAbsoluteEpochMillis)
        assertEquals(TriggerRelation.Start, entity.triggerRelatedTo)
    }

    @Test
    fun relatedToEnd_isPreserved() {
        val remote = alarm(triggerDuration = "PT0S", triggerRelatedTo = "END")
        val entity = remoteEvent(alarms = listOf(remote)).toEntity(calendarId).alarms.single()

        assertEquals(0L, entity.triggerRelativeMillis)
        assertEquals(TriggerRelation.End, entity.triggerRelatedTo)
    }

    @Test
    fun absoluteTrigger_isParsedToEpochMillis_andRelatedToFallsBackToStart() {
        val remote = alarm(triggerDuration = null, triggerAbsolute = "20260615T090000Z", triggerRelatedTo = "")
        val entity = remoteEvent(alarms = listOf(remote)).toEntity(calendarId).alarms.single()

        assertNull(entity.triggerRelativeMillis)
        assertEquals(1_781_514_000_000L, entity.triggerAbsoluteEpochMillis)
        assertEquals(TriggerRelation.Start, entity.triggerRelatedTo)
    }

    @Test
    fun rawIcs_preservationHappensAtWholeListLevel_notPerAlarm() {
        val block = "BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nX-CUSTOM:keep-me\r\nEND:VALARM\r\n"
        val remote = alarm(triggerDuration = "-PT15M", rawIcs = block)
        val entity = remoteEvent(alarms = listOf(remote)).toEntity(calendarId).alarms.single()

        assertEquals(-15L * 60_000L, entity.triggerRelativeMillis)
    }

    @Test
    fun action_isUppercased_evenForUnknownValues() {
        val remote = alarm(action = "procedure", triggerDuration = "-PT5M")
        val entity = remoteEvent(alarms = listOf(remote)).toEntity(calendarId).alarms.single()

        assertEquals("PROCEDURE", entity.action)
    }

    @Test
    fun multipleAlarms_arePreservedInOrder() {
        val firstAlarm = alarm(triggerDuration = "-PT30M", description = "First")
        val secondAlarm = alarm(triggerDuration = "-PT5M", description = "Second")
        val entity = remoteEvent(alarms = listOf(firstAlarm, secondAlarm)).toEntity(calendarId)

        assertEquals(listOf("First", "Second"), entity.alarms.map { it.description })
    }


    private fun alarm(
        action: String = "DISPLAY",
        triggerDuration: String? = "-PT15M",
        triggerAbsolute: String? = null,
        triggerRelatedTo: String = "START",
        description: String? = "Reminder",
        summary: String? = null,
        attendees: List<String> = emptyList(),
        attach: String? = null,
        @Suppress("UNUSED_PARAMETER") rawIcs: String = "",
    ) = RemoteDavAlarm(
        action = action,
        triggerDuration = triggerDuration,
        triggerAbsolute = triggerAbsolute,
        triggerRelatedTo = triggerRelatedTo,
        description = description,
        summary = summary,
        attendees = attendees,
        attach = attach,
    )

    private fun remoteEvent(alarms: List<RemoteDavAlarm>) = RemoteDavEvent(
        url = "https://cal/tests/alarm.ics",
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
        attendees = emptyList(),
        alarms = alarms,
    )
}
