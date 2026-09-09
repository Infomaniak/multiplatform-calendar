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

import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventDotColorInRange
import com.infomaniak.multiplatform_calendar.core.data.local.projection.OverrideDotColorInRange
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.DotColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DotColorsByDayFoldTest {

    private val utc = TimeZone.UTC
    private val dayStart = LocalDateTime(2026, 6, 15, 0, 0)
    private val dayEnd = LocalDateTime(2026, 6, 16, 0, 0)

    private val red = 0xFFE53935.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val green = 0xFF43A047.toInt()

    @Test
    fun foldToDailyDotColors_ordersColorsByPerDayEventSort_notInputOrder() = runTest {
        val rows = listOf(
            row(eventId = "event://blue-09", calendarId = "calendar://blue", calendarColor = blue, startHour = 9, endHour = 10),
            row(eventId = "event://red-08", calendarId = "calendar://red", calendarColor = red, startHour = 8, endHour = 9),
        )

        val result = rows.foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(red, blue), result.sourceColorsOn(dayStart))
    }

    @Test
    fun foldToDailyDotColors_keepsEarliestKeyPerColor_beforeFinalColorSort() = runTest {
        val rows = listOf(
            row(eventId = "event://red-15", calendarId = "calendar://red", calendarColor = red, startHour = 15, endHour = 16),
            row(eventId = "event://blue-10", calendarId = "calendar://blue", calendarColor = blue, startHour = 10, endHour = 11),
            row(eventId = "event://red-08", calendarId = "calendar://red", calendarColor = red, startHour = 8, endHour = 9),
        )

        val result = rows.foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(red, blue), result.sourceColorsOn(dayStart))
    }

    @Test
    fun foldToDailyDotColors_dotsEventColor_whenItOverridesItsCalendarColor() = runTest {
        val rows = listOf(
            row(eventId = "event://inherited-08", calendarColor = blue, startHour = 8, endHour = 9),
            row(eventId = "event://recolored-10", calendarColor = blue, eventColor = red, startHour = 10, endHour = 11),
            row(eventId = "event://inherited-12", calendarColor = blue, startHour = 12, endHour = 13),
        )

        val result = rows.foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        // Two effective colors for one calendar: two dots, and the third event joins the first one's dot.
        assertEquals(listOf(blue, red), result.sourceColorsOn(dayStart))
    }

    @Test
    fun foldToDailyDotColors_keepsOneDotPerCalendar_whenTwoCalendarsShareSameColor() = runTest {
        val rows = listOf(
            row(eventId = "event://red1-08", calendarId = "calendar://red1", calendarColor = red, startHour = 8, endHour = 9),
            row(eventId = "event://red2-10", calendarId = "calendar://red2", calendarColor = red, startHour = 10, endHour = 11),
        )

        val result = rows.foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(
            listOf(red, red),
            result.sourceColorsOn(dayStart),
            "each calendar owns its dot, even when both dots end up the same color",
        )
    }

    @Test
    fun foldToDailyDotColors_collapsesToOneDot_whenOneCalendarRepeatsItsColor() = runTest {
        val rows = listOf(
            row(eventId = "event://red-08", calendarColor = red, startHour = 8, endHour = 9),
            row(eventId = "event://red-10", calendarColor = red, startHour = 10, endHour = 11),
        )

        val result = rows.foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(red), result.sourceColorsOn(dayStart))
    }

    @Test
    fun foldToDailyDotColors_usesDefaultColor_whenNeitherEventNorCalendarDeclaresOne() = runTest {
        val rows = listOf(row(eventId = "event://colorless", calendarColor = null, startHour = 8, endHour = 9))

        val result = rows.foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(CalendarColors.DEFAULT_SOURCE_COLOR), result.sourceColorsOn(dayStart))
    }

    @Test
    fun foldToDailyDotColors_ordersAllDayBeforeTimed() = runTest {
        val rows = listOf(
            row(eventId = "event://timed", calendarId = "calendar://blue", calendarColor = blue, startHour = 8, endHour = 9),
            allDayRow(eventId = "event://all-day", calendarId = "calendar://red", calendarColor = red),
        )

        val result = rows.foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = dayEnd.toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(red, blue), result.sourceColorsOn(dayStart))
    }

    @Test
    fun foldToDailyDotColors_orphanOverrideMovedIntoRange_doesNotDotItsDay() = runTest {
        val orphanDay = LocalDateTime(2026, 6, 16, 10, 0)

        val master = row(eventId = "event://bounded", calendarColor = red, startHour = 8, endHour = 9)
            .copy(
                rrule = RecurrenceRule(
                    freq = Frequency.Daily,
                    until = RecurrenceUntil.DateTimeUtc(LocalDateTime(2026, 6, 15, 23, 59, 59).toInstant(utc)),
                ),
                overrides = listOf(
                    OverrideDotColorInRange(
                        recurrenceKey = RecurrenceKey.Zoned(LocalDateTime(2026, 6, 20, 8, 0), utc.id),
                        dtStart = orphanDay,
                        dtEndEffective = orphanDay,
                        startTimeZone = utc.id,
                        endTimeZone = utc.id,
                        isAllDay = false,
                        colorArgb = null,
                        status = null,
                    ),
                ),
            )

        val result = listOf(master).foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = LocalDateTime(2026, 6, 17, 0, 0).toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(red), result.sourceColorsOn(dayStart))
        assertNull(result[orphanDay.date])
    }

    @Test
    fun foldToDailyDotColors_exDatedOccurrence_isNotDotted() = runTest {
        val excluded = LocalDateTime(2026, 6, 16, 8, 0)

        val master = row(eventId = "event://daily", calendarColor = red, startHour = 8, endHour = 9)
            .copy(
                rrule = RecurrenceRule(freq = Frequency.Daily),
                exDates = listOf(IcalDateValue.Zoned(excluded.toInstant(utc), utc.id)),
            )

        val result = listOf(master).foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = LocalDateTime(2026, 6, 17, 0, 0).toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(red), result.sourceColorsOn(dayStart))
        assertNull(result[excluded.date])
    }

    @Test
    fun foldToDailyDotColors_rDatedOccurrence_isDotted() = runTest {
        val added = LocalDateTime(2026, 6, 16, 8, 0)

        val master = row(eventId = "event://rdated", calendarColor = red, startHour = 8, endHour = 9)
            .copy(rDates = listOf(IcalDateValue.Zoned(added.toInstant(utc), utc.id)))

        val result = listOf(master).foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = LocalDateTime(2026, 6, 17, 0, 0).toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(red), result.sourceColorsOn(dayStart))
        assertEquals(listOf(red), result.sourceColorsOn(added))
    }

    @Test
    fun foldToDailyDotColors_dotsOverrideOwnColor_onTheDayItLandsOn() = runTest {
        val overriddenSlot = LocalDateTime(2026, 6, 16, 8, 0)
        val movedTo = LocalDateTime(2026, 6, 17, 14, 0)

        val master = row(eventId = "event://daily", calendarColor = blue, startHour = 8, endHour = 9)
            .copy(
                rrule = RecurrenceRule(freq = Frequency.Daily),
                overrides = listOf(
                    override(
                        recurrenceKey = RecurrenceKey.Utc(overriddenSlot.toInstant(utc)),
                        start = movedTo,
                        end = LocalDateTime(2026, 6, 17, 15, 0),
                        colorArgb = green,
                    ),
                ),
            )

        val result = listOf(master).foldToDailyDotColors(
            rangeStart = dayStart.toInstant(utc),
            rangeEnd = LocalDateTime(2026, 6, 18, 0, 0).toInstant(utc),
            timeZone = utc,
        )

        assertEquals(listOf(blue), result.sourceColorsOn(dayStart))
        assertNull(result[overriddenSlot.date]) // The slot it left is undotted
        assertEquals(listOf(blue, green), result.sourceColorsOn(movedTo))
    }

    private fun Map<LocalDate, List<DotColor>>.sourceColorsOn(dateTime: LocalDateTime): List<Int> {
        return getValue(dateTime.date).map { it.sourceColor }
    }

    private fun row(
        eventId: String,
        calendarId: String = "calendar://default",
        calendarColor: Int?,
        eventColor: Int? = null,
        startHour: Int,
        endHour: Int,
    ): EventDotColorInRange {
        return EventDotColorInRange(
            eventId = EventId(eventId),
            calendarId = CalendarId(calendarId),
            calendarColorArgb = calendarColor,
            eventColorArgb = eventColor,
            dtStart = LocalDateTime(2026, 6, 15, startHour, 0),
            dtEndEffective = LocalDateTime(2026, 6, 15, endHour, 0),
            startZoneId = utc.id,
            endZoneId = utc.id,
            isAllDay = false,
            rrule = null,
            rDates = emptyList(),
            exDates = emptyList(),
        )
    }

    private fun allDayRow(eventId: String, calendarId: String, calendarColor: Int?): EventDotColorInRange {
        return EventDotColorInRange(
            eventId = EventId(eventId),
            calendarId = CalendarId(calendarId),
            calendarColorArgb = calendarColor,
            eventColorArgb = null,
            dtStart = LocalDateTime(2026, 6, 15, 0, 0),
            dtEndEffective = LocalDateTime(2026, 6, 16, 0, 0),
            startZoneId = null,
            endZoneId = null,
            isAllDay = true,
            rrule = null,
            rDates = emptyList(),
            exDates = emptyList(),
        )
    }

    private fun override(
        recurrenceKey: RecurrenceKey,
        start: LocalDateTime,
        end: LocalDateTime,
        colorArgb: Int?,
    ): OverrideDotColorInRange {
        return OverrideDotColorInRange(
            recurrenceKey = recurrenceKey,
            dtStart = start,
            dtEndEffective = end,
            startTimeZone = utc.id,
            endTimeZone = utc.id,
            isAllDay = false,
            colorArgb = colorArgb,
            status = null,
        )
    }
}
