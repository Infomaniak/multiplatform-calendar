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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventSourceColor
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteColorChange
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventEditMapperTest {

    private val calendarId = CalendarId("calendar://edit")
    private val paris = TimeZone.of("Europe/Paris")
    private val newYork = TimeZone.of("America/New_York")

    // ---- DTSTART / DTEND value forms (RFC 5545 §3.3.4-5) ----------------------------------------

    @Test
    fun allDayEdit_emitsDateForms_andDropsTzids() {
        val edit = editData(
            timing = EventTiming(
                start = LocalDateTime(2026, 6, 15, 0, 0),
                end = LocalDateTime(2026, 6, 16, 0, 0),
                startTimeZone = null,
                endTimeZone = null,
                isAllDay = true,
            ),
        ).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals("20260615", edit.dtStart)
        assertEquals("20260616", edit.dtEnd)
        assertNull(edit.dtStartTzid)
        assertNull(edit.dtEndTzid)
        assertTrue(edit.timeZones.isEmpty())
        assertEquals(true, edit.allDay)
    }

    @Test
    fun utcEdit_emitsForm2_withZSuffix_andNoTzid() {
        val edit = editData(
            timing = EventTiming(
                start = LocalDateTime(2026, 6, 15, 10, 0),
                end = LocalDateTime(2026, 6, 15, 11, 0),
                startTimeZone = TimeZone.UTC,
                endTimeZone = TimeZone.UTC,
                isAllDay = false,
            ),
        ).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals("20260615T100000Z", edit.dtStart)
        assertEquals("20260615T110000Z", edit.dtEnd)
        assertNull(edit.dtStartTzid)
        assertNull(edit.dtEndTzid)
        // UTC uses the `Z` suffix; no VTIMEZONE needed.
        assertTrue(edit.timeZones.isEmpty())
    }

    @Test
    fun zonedEdit_emitsForm3_withLocalDateTime_andTzid_andSingleVTimeZone() {
        val edit = editData(
            timing = EventTiming(
                start = LocalDateTime(2026, 6, 15, 14, 0),
                end = LocalDateTime(2026, 6, 15, 15, 0),
                startTimeZone = paris,
                endTimeZone = paris,
                isAllDay = false,
            ),
        ).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals("20260615T140000", edit.dtStart)
        assertEquals("20260615T150000", edit.dtEnd)
        assertEquals("Europe/Paris", edit.dtStartTzid)
        assertEquals("Europe/Paris", edit.dtEndTzid)
        // Same zone start & end → single VTIMEZONE entry (deduplicated).
        assertEquals(1, edit.timeZones.size)
        assertEquals("Europe/Paris", edit.timeZones.first().tzid)
        // Paris summer = UTC+2 → formatted per UtcOffset.Formats.FOUR_DIGITS.
        assertEquals("+0200", edit.timeZones.first().offset)
    }

    @Test
    fun floatingEdit_emitsForm1_noZ_noTzid_noVTimeZone() {
        val edit = editData(
            timing = EventTiming(
                start = LocalDateTime(2026, 6, 15, 10, 0),
                end = LocalDateTime(2026, 6, 15, 11, 0),
                startTimeZone = null,
                endTimeZone = null,
                isAllDay = false,
            ),
        ).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals("20260615T100000", edit.dtStart)
        assertEquals("20260615T110000", edit.dtEnd)
        assertNull(edit.dtStartTzid)
        assertNull(edit.dtEndTzid)
        assertTrue(edit.timeZones.isEmpty())
    }

    // ---- Cross-zone (RFC 5545 §3.8.2.2) ---------------------------------------------------------

    @Test
    fun crossZoneFlight_emitsTwoTzids_andTwoVTimeZones() {
        val edit = editData(
            timing = EventTiming(
                start = LocalDateTime(2026, 6, 15, 9, 0),
                end = LocalDateTime(2026, 6, 15, 21, 0),
                startTimeZone = newYork,
                endTimeZone = paris,
                isAllDay = false,
            ),
        ).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals("America/New_York", edit.dtStartTzid)
        assertEquals("Europe/Paris", edit.dtEndTzid)
        // Two distinct zones → both VTIMEZONE definitions emitted.
        assertEquals(2, edit.timeZones.size)
        val tzids = edit.timeZones.map { it.tzid }.toSet()
        assertEquals(setOf("America/New_York", "Europe/Paris"), tzids)
        // NY summer = UTC-4 ; Paris summer = UTC+2.
        val byTzid = edit.timeZones.associate { it.tzid to it.offset }
        assertEquals("-0400", byTzid.getValue("America/New_York"))
        assertEquals("+0200", byTzid.getValue("Europe/Paris"))
    }

    @Test
    fun winterEvent_emitsWinterOffset_notSummerOffset() {
        // February in Paris = UTC+1 (winter time). Offset must be sampled at the event's own wall-clock.
        val edit = editData(
            timing = EventTiming(
                start = LocalDateTime(2026, 2, 15, 14, 0),
                end = LocalDateTime(2026, 2, 15, 15, 0),
                startTimeZone = paris,
                endTimeZone = paris,
                isAllDay = false,
            ),
        ).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals("+0100", edit.timeZones.single().offset)
    }

    // ---- Per-event color (RFC 7986 `COLOR` + Apple `X-APPLE-CALENDAR-COLOR`) --------------------

    @Test
    fun createWithoutColor_emitsUnchanged() {
        val edit = editData(eventColor = null).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals(RemoteColorChange.Unchanged, edit.colorChange)
    }

    @Test
    fun createWithColor_emitsSetWithAppleHex() {
        val edit = editData(eventColor = 0xFF1E88E5.toInt()).toRemoteEdit(previous = null, stamp = STAMP)

        assertEquals(RemoteColorChange.Set(hex = "#1E88E5FF"), edit.colorChange)
    }

    @Test
    fun editWithSameColor_emitsUnchanged_toPreserveWireRepresentation() {
        val previous = eventEntity(colorArgb = 0xFF1E88E5.toInt(), colorIcalName = "royalblue")

        val edit = editData(eventColor = 0xFF1E88E5.toInt()).toRemoteEdit(previous = previous, stamp = STAMP)

        assertEquals(RemoteColorChange.Unchanged, edit.colorChange)
    }

    @Test
    fun editClearingColor_emitsCleared() {
        val previous = eventEntity(colorArgb = 0xFF1E88E5.toInt())

        val edit = editData(eventColor = null).toRemoteEdit(previous = previous, stamp = STAMP)

        assertEquals(RemoteColorChange.Cleared, edit.colorChange)
    }

    @Test
    fun editChangingColor_emitsSetWithNewAppleHex() {
        val previous = eventEntity(colorArgb = 0xFF1E88E5.toInt(), colorIcalName = "royalblue")

        val edit = editData(eventColor = 0xFFE53935.toInt()).toRemoteEdit(previous = previous, stamp = STAMP)

        assertEquals(RemoteColorChange.Set(hex = "#E53935FF"), edit.colorChange)
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun editData(timing: EventTiming) = EventEditData(
        title = "Test",
        timing = timing,
        location = null,
        description = null,
        calendarId = calendarId,
        eventColor = null,
        alarms = emptyList(),
    )

    private fun editData(eventColor: Int?) = EventEditData(
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
        eventColor = eventColor?.let(::EventSourceColor),
        alarms = emptyList(),
    )

    private fun eventEntity(colorArgb: Int?, colorIcalName: String? = null) = EventEntity(
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
        rawIcs = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
        colorArgb = colorArgb,
        colorIcalName = colorIcalName,
    )

    private companion object {
        const val STAMP = "20260615T090000Z"
    }
}
