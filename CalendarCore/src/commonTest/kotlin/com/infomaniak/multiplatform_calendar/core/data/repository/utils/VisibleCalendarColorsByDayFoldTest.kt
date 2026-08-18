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
package com.infomaniak.multiplatform_calendar.core.data.repository.utils

import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventCalendarColorInRange
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleCalendarColorsByDayFoldTest {

    private val utc = TimeZone.UTC
    private val dayStart = LocalDateTime(2026, 6, 15, 0, 0)
    private val dayEnd = LocalDateTime(2026, 6, 16, 0, 0)

    @Test
    fun foldToDailyCalendarColors_ordersColorsByPerDayEventSort_notInputOrder() = runTest {
        val red = 0xFFE53935.toInt()
        val blue = 0xFF1E88E5.toInt()

        val rows = listOf(
            row(eventId = "event://blue-09", calendarId = "calendar://blue", color = blue, startHour = 9, endHour = 10),
            row(eventId = "event://red-08", calendarId = "calendar://red", color = red, startHour = 8, endHour = 9),
        )

        val result = rows.foldToDailyCalendarColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(
            listOf(red, blue),
            result.getValue(dayStart.date).map { it.colors.calendarSourceColor },
        )
        assertEquals(
            listOf(CalendarId("calendar://red"), CalendarId("calendar://blue")),
            result.getValue(dayStart.date).map { it.id },
        )
    }

    @Test
    fun foldToDailyCalendarColors_keepsEarliestKeyPerColor_beforeFinalColorSort() = runTest {
        val red = 0xFFE53935.toInt()
        val blue = 0xFF1E88E5.toInt()

        val rows = listOf(
            row(eventId = "event://red-15", calendarId = "calendar://red", color = red, startHour = 15, endHour = 16),
            row(eventId = "event://blue-10", calendarId = "calendar://blue", color = blue, startHour = 10, endHour = 11),
            row(eventId = "event://red-08", calendarId = "calendar://red", color = red, startHour = 8, endHour = 9),
        )

        val result = rows.foldToDailyCalendarColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(
            listOf(red, blue),
            result.getValue(dayStart.date).map { it.colors.calendarSourceColor },
        )
    }

    @Test
    fun foldToDailyCalendarColors_keepsBothCalendarIds_whenTwoCalendarsShareSameColor() = runTest {
        val red = 0xFFE53935.toInt()

        val rows = listOf(
            row(eventId = "event://red1-08", calendarId = "calendar://red1", color = red, startHour = 8, endHour = 9),
            row(eventId = "event://red2-10", calendarId = "calendar://red2", color = red, startHour = 10, endHour = 11),
        )

        val result = rows.foldToDailyCalendarColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        val dayEntries = result.getValue(dayStart.date)
        assertEquals(2, dayEntries.size)
        assertEquals(
            listOf(CalendarId("calendar://red1"), CalendarId("calendar://red2")),
            dayEntries.map { it.id },
        )
        assertEquals(
            listOf(red, red),
            dayEntries.map { it.colors.calendarSourceColor },
        )
    }

    @Test
    fun foldToDailyCalendarColors_ordersAllDayBeforeTimed() = runTest {
        val red = 0xFFE53935.toInt()
        val blue = 0xFF1E88E5.toInt()

        val rows = listOf(
            row(eventId = "event://timed", calendarId = "calendar://blue", color = blue, startHour = 8, endHour = 9),
            allDayRow(eventId = "event://all-day", calendarId = "calendar://red", color = red),
        )

        val result = rows.foldToDailyCalendarColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(
            listOf(red, blue),
            result.getValue(dayStart.date).map { it.colors.calendarSourceColor },
        )
    }

    private fun row(
        eventId: String,
        calendarId: String,
        color: Int,
        startHour: Int,
        endHour: Int,
    ): EventCalendarColorInRange {
        return EventCalendarColorInRange(
            eventId = EventId(eventId),
            calendarId = CalendarId(calendarId),
            colorArgb = color,
            dtStart = LocalDateTime(2026, 6, 15, startHour, 0),
            dtEndEffective = LocalDateTime(2026, 6, 15, endHour, 0),
            startZoneId = utc.id,
            endZoneId = utc.id,
            isAllDay = false,
            rrule = null,
        )
    }

    private fun allDayRow(eventId: String, calendarId: String, color: Int): EventCalendarColorInRange {
        return EventCalendarColorInRange(
            eventId = EventId(eventId),
            calendarId = CalendarId(calendarId),
            colorArgb = color,
            dtStart = LocalDateTime(2026, 6, 15, 0, 0),
            dtEndEffective = LocalDateTime(2026, 6, 16, 0, 0),
            startZoneId = null,
            endZoneId = null,
            isAllDay = true,
            rrule = null,
        )
    }
}

