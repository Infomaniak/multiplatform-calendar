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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.WeekDayNum
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * [resolveRecurrence] returns a typed [RecurrenceRule], `null` when the event has no `RRULE`, or
 * throws [RecurrenceDroppedException] when a present rule must be dropped — including when the
 * `UNTIL` value form disagrees with `DTSTART` (DATE / UTC-zoned / floating, RFC 5545 §3.3.10).
 */
class RemoteDavEventToEntityRecurrenceTest {

    @Test
    fun supportedRule_isParsed() {
        val rule = remoteEvent(rrule = "FREQ=WEEKLY;BYDAY=MO").resolveRecurrence()

        assertEquals(
            RecurrenceRule(freq = Frequency.Weekly, byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.MONDAY))),
            rule,
        )
    }

    @Test
    fun absentRule_returnsNull() {
        assertNull(remoteEvent(rrule = null).resolveRecurrence())
    }

    @Test
    fun unparseableRule_throwsWithReason() {
        assertDropped(RecurrenceRuleFailureReason.MissingFrequency) {
            remoteEvent(rrule = "COUNT=1").resolveRecurrence()
        }
    }

    @Test
    fun dateTimeUntil_onTimedEvent_isSupported() {
        val rule = remoteEvent(
            rrule = "FREQ=DAILY;UNTIL=20260701T000000Z",
            dtstart = "20260615T100000Z",
        ).resolveRecurrence()

        assertEquals(Frequency.Daily, rule?.freq)
    }

    @Test
    fun floatingUntil_onUtcEvent_isDroppedAsMismatch() {
        assertDropped(RecurrenceRuleFailureReason.UntilTypeMismatch) {
            remoteEvent(rrule = "FREQ=DAILY;UNTIL=20260701T000000", dtstart = "20260615T100000Z").resolveRecurrence()
        }
    }

    @Test
    fun utcUntil_onZonedEvent_isSupported() {
        val rule = remoteEvent(
            rrule = "FREQ=DAILY;UNTIL=20260701T000000Z",
            dtstart = "20260615T100000",
            dtStartTzid = "Europe/Paris",
        ).resolveRecurrence()

        assertEquals(Frequency.Daily, rule?.freq)
    }

    @Test
    fun floatingUntil_onZonedEvent_isDroppedAsMismatch() {
        assertDropped(RecurrenceRuleFailureReason.UntilTypeMismatch) {
            remoteEvent(
                rrule = "FREQ=DAILY;UNTIL=20260701T000000",
                dtstart = "20260615T100000",
                dtStartTzid = "Europe/Paris",
            ).resolveRecurrence()
        }
    }

    @Test
    fun floatingUntil_onFloatingEvent_isSupported() {
        val rule = remoteEvent(
            rrule = "FREQ=DAILY;UNTIL=20260701T000000",
            dtstart = "20260615T100000",
        ).resolveRecurrence()

        assertEquals(Frequency.Daily, rule?.freq)
    }

    @Test
    fun utcUntil_onFloatingEvent_isDroppedAsMismatch() {
        assertDropped(RecurrenceRuleFailureReason.UntilTypeMismatch) {
            remoteEvent(rrule = "FREQ=DAILY;UNTIL=20260701T000000Z", dtstart = "20260615T100000").resolveRecurrence()
        }
    }

    @Test
    fun dateOnlyUntil_onTimedEvent_isDroppedAsMismatch() {
        assertDropped(RecurrenceRuleFailureReason.UntilTypeMismatch) {
            remoteEvent(rrule = "FREQ=DAILY;UNTIL=20260701", dtstart = "20260615T100000Z").resolveRecurrence()
        }
    }

    @Test
    fun dateOnlyUntil_onAllDayEvent_isSupported() {
        val rule = remoteEvent(
            rrule = "FREQ=DAILY;UNTIL=20260701",
            dtstart = "20260615",
            dtend = "20260616",
        ).resolveRecurrence()

        assertEquals(Frequency.Daily, rule?.freq)
    }

    private inline fun assertDropped(expected: RecurrenceRuleFailureReason, block: () -> Unit) {
        val exception = assertFailsWith<RecurrenceDroppedException>(block = block)
        assertEquals(expected.name, exception.reason)
    }

    private fun remoteEvent(
        rrule: String?,
        dtstart: String? = "20260615T100000Z",
        dtend: String? = "20260615T110000Z",
        dtStartTzid: String? = null,
    ) = RemoteDavEvent(
        url = "https://cal/tests/recurrence.ics",
        etag = "etag-1",
        icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
        uid = "uid-1",
        summary = "Test",
        description = null,
        location = null,
        dtstart = dtstart,
        dtStartTzid = dtStartTzid,
        dtend = dtend,
        dtEndTzid = null,
        duration = null,
        created = null,
        lastModified = null,
        dtstamp = null,
        rrule = rrule,
        status = null,
        transp = null,
        classification = null,
        priority = null,
        sequence = null,
        categories = null,
        colorHex = null,
        colorIcalName = null,
        attendees = emptyList(),
        alarms = emptyList(),
    )
}
