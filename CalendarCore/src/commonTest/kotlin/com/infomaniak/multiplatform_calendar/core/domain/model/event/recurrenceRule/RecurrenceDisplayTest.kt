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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RecurrenceDisplayTest {

    private val paris = TimeZone.of("Europe/Paris")
    private val newYork = TimeZone.of("America/New_York")

    // DTSTART anchor used by every test: Thursday 6 August 2026, 10:00.
    private val anchor = LocalDateTime(2026, 8, 6, 10, 0)

    // ---- Simple frequencies ---------------------------------------------------------------------

    @Test
    fun daily_withoutByRules_isSimpleDay() {
        val display = rule(Frequency.Daily, interval = 3).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Simple(3, RecurrenceUnit.Day), display.pattern)
        assertEquals(RecurrenceEnd.Never, display.end)
    }

    @Test
    fun hourly_withoutByRules_isSimpleHour() {
        val display = rule(Frequency.Hourly, interval = 2).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Simple(2, RecurrenceUnit.Hour), display.pattern)
    }

    @Test
    fun daily_withByRules_isCustom() {
        // FREQ=DAILY;BYMONTHDAY=1 is valid but not worth a sentence.
        val display = rule(Frequency.Daily, byMonthDay = listOf(1)).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    @Test
    fun interval_zero_isCoercedToOne() {
        val display = rule(Frequency.Daily, interval = 0).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Simple(1, RecurrenceUnit.Day), display.pattern)
    }

    // ---- Weekly ---------------------------------------------------------------------------------

    @Test
    fun weekly_sortsDaysFromGivenWeekStart_notFromRuleWkst() {
        // "Every 4 weeks on Monday, Tuesday, Wednesday, Thursday and Friday", listed out of order
        // and with a WKST that must not leak into the display order.
        val weekly = rule(
            freq = Frequency.Weekly,
            interval = 4,
            byDay = listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.WEDNESDAY)
                .map { WeekDayNum(dayOfWeek = it) },
            weekStart = DayOfWeek.TUESDAY,
        )

        val fromMonday = weekly.toDisplay(timing(), paris, weekStart = DayOfWeek.MONDAY).pattern
        val fromSunday = weekly.toDisplay(timing(), paris, weekStart = DayOfWeek.SUNDAY).pattern

        val expectedFromMonday = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        assertEquals(RecurrencePattern.Weekly(4, expectedFromMonday), fromMonday)
        // Sunday-first locales get the same list here, Monday still being the earliest weekday.
        assertEquals(RecurrencePattern.Weekly(4, expectedFromMonday), fromSunday)
    }

    @Test
    fun weekly_weekStartShiftsOrder_whenItSplitsTheList() {
        val weekly = rule(
            freq = Frequency.Weekly,
            byDay = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).map { WeekDayNum(dayOfWeek = it) },
        )

        assertEquals(
            RecurrencePattern.Weekly(1, listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)),
            weekly.toDisplay(timing(), paris, weekStart = DayOfWeek.MONDAY).pattern,
        )
        assertEquals(
            RecurrencePattern.Weekly(1, listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)),
            weekly.toDisplay(timing(), paris, weekStart = DayOfWeek.SUNDAY).pattern,
        )
    }

    @Test
    fun weekly_withoutByDay_fallsBackToDtstartDay() {
        val display = rule(Frequency.Weekly, interval = 2).toDisplay(timing(), paris)

        // The anchor is a Thursday.
        assertEquals(RecurrencePattern.Weekly(2, listOf(DayOfWeek.THURSDAY)), display.pattern)
    }

    @Test
    fun weekly_deduplicatesDays() {
        val weekly = rule(
            freq = Frequency.Weekly,
            byDay = listOf(DayOfWeek.MONDAY, DayOfWeek.MONDAY).map { WeekDayNum(dayOfWeek = it) },
        )

        assertEquals(RecurrencePattern.Weekly(1, listOf(DayOfWeek.MONDAY)), weekly.toDisplay(timing(), paris).pattern)
    }

    @Test
    fun weekly_withNumberedByDay_isCustom() {
        // RFC 5545 §3.3.10 forbids a numbered BYDAY outside MONTHLY/YEARLY.
        val weekly = rule(
            freq = Frequency.Weekly,
            byDay = listOf(WeekDayNum(ordinal = 2, dayOfWeek = DayOfWeek.MONDAY)),
        )

        assertEquals(RecurrencePattern.Custom, weekly.toDisplay(timing(), paris).pattern)
    }

    // ---- Monthly --------------------------------------------------------------------------------

    @Test
    fun monthly_withoutByRules_derivesDayFromDtstart() {
        val display = rule(Frequency.Monthly).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Monthly(1, MonthDayRule.DayOfMonth(6)), display.pattern)
    }

    @Test
    fun monthly_withByMonthDay_usesIt() {
        val display = rule(Frequency.Monthly, interval = 2, byMonthDay = listOf(15)).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Monthly(2, MonthDayRule.DayOfMonth(15)), display.pattern)
    }

    @Test
    fun monthly_withLastDayOfMonth_hasItsOwnCase() {
        // BYMONTHDAY=-1: month length varies, so there is no day number to show.
        val display = rule(Frequency.Monthly, byMonthDay = listOf(-1)).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Monthly(1, MonthDayRule.LastDayOfMonth), display.pattern)
    }

    @Test
    fun monthly_withOtherNegativeByMonthDay_isCustom() {
        // "The 2nd to last day" has no natural short wording.
        val display = rule(Frequency.Monthly, byMonthDay = listOf(-2)).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    @Test
    fun monthly_withNumberedByDay_isPositionedWeekday() {
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(WeekDayNum(ordinal = 1, dayOfWeek = DayOfWeek.THURSDAY)),
        ).toDisplay(timing(), paris)

        val expected = MonthDayRule.Weekdays(listOf(DayOfWeek.THURSDAY), WeekdayPosition.First)
        assertEquals(RecurrencePattern.Monthly(1, expected), display.pattern)
    }

    @Test
    fun monthly_withByDayWithoutPosition_meansEveryMatchingWeekday() {
        // FREQ=MONTHLY;BYDAY=MO is *all* the Mondays of the month, not the first one.
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.MONDAY)),
        ).toDisplay(timing(), paris)

        val expected = MonthDayRule.Weekdays(listOf(DayOfWeek.MONDAY), position = null)
        assertEquals(RecurrencePattern.Monthly(1, expected), display.pattern)
    }

    @Test
    fun monthly_withSeveralEntryOrdinals_isCustom() {
        // BYDAY=1MO,3MO is two positions: one sentence cannot carry both.
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(
                WeekDayNum(ordinal = 1, dayOfWeek = DayOfWeek.MONDAY),
                WeekDayNum(ordinal = 3, dayOfWeek = DayOfWeek.MONDAY),
            ),
        ).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    @Test
    fun monthly_withByDayAndByMonthDay_isCustom() {
        // "Friday the 13th": an intersection of two axes.
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.FRIDAY)),
            byMonthDay = listOf(13),
        ).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    @Test
    fun monthly_withBySetPos_isEquivalentToNumberedByDay() {
        // BYDAY=FR;BYSETPOS=-1 ≡ BYDAY=-1FR
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.FRIDAY)),
            byOccurrencePosition = listOf(-1),
        ).toDisplay(timing(), paris)

        val expected = MonthDayRule.Weekdays(listOf(DayOfWeek.FRIDAY), WeekdayPosition.Last)
        assertEquals(RecurrencePattern.Monthly(1, expected), display.pattern)
    }

    @Test
    fun monthly_withBySetPosOverASet_isTheLastWorkingDay() {
        // BYDAY=MO..FR;BYSETPOS=-1: the position applies to the whole set, so this is "the last
        // working day" — which is not "the last Monday".
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
                .map { WeekDayNum(dayOfWeek = it) },
            byOccurrencePosition = listOf(-1),
        ).toDisplay(timing(), paris, weekStart = DayOfWeek.MONDAY)

        val expectedDays = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        val expected = MonthDayRule.Weekdays(expectedDays, WeekdayPosition.Last)
        assertEquals(RecurrencePattern.Monthly(1, expected), display.pattern)
    }

    @Test
    fun monthly_withSeveralBySetPos_isCustom() {
        // "The first and the last" needs two positions in one sentence.
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.FRIDAY)),
            byOccurrencePosition = listOf(1, -1),
        ).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    @Test
    fun monthly_withNegativeOrdinalOtherThanLast_isCustom() {
        // "Second-to-last Monday" has no natural short wording.
        val display = rule(
            freq = Frequency.Monthly,
            byDay = listOf(WeekDayNum(ordinal = -2, dayOfWeek = DayOfWeek.MONDAY)),
        ).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    @Test
    fun monthly_withByMonth_isCustom() {
        val display = rule(Frequency.Monthly, byMonth = listOf(8)).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    // ---- Yearly ---------------------------------------------------------------------------------

    @Test
    fun yearly_withoutByRules_derivesMonthAndDayFromDtstart() {
        val display = rule(Frequency.Yearly).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Yearly(1, Month.AUGUST, MonthDayRule.DayOfMonth(6)), display.pattern)
    }

    @Test
    fun yearly_withSingleByMonth_usesIt() {
        val display = rule(
            freq = Frequency.Yearly,
            interval = 2,
            byMonth = listOf(11),
            byDay = listOf(WeekDayNum(ordinal = -1, dayOfWeek = DayOfWeek.FRIDAY)),
        ).toDisplay(timing(), paris)

        val expected = MonthDayRule.Weekdays(listOf(DayOfWeek.FRIDAY), WeekdayPosition.Last)
        assertEquals(RecurrencePattern.Yearly(2, Month.NOVEMBER, expected), display.pattern)
    }

    @Test
    fun yearly_withMultipleByMonth_isCustom() {
        val display = rule(Frequency.Yearly, byMonth = listOf(3, 9)).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    @Test
    fun yearly_withInvalidByMonth_isCustom() {
        val display = rule(Frequency.Yearly, byMonth = listOf(13)).toDisplay(timing(), paris)

        assertEquals(RecurrencePattern.Custom, display.pattern)
    }

    // ---- End ------------------------------------------------------------------------------------

    @Test
    fun end_countTakesPrecedenceOverUntil() {
        // The parser rejects COUNT+UNTIL, but the model can still be built by hand.
        val display = rule(
            freq = Frequency.Daily,
            occurrenceCount = 5,
            until = RecurrenceUntil.DateOnly(LocalDate(2026, 12, 31)),
        ).toDisplay(timing(), paris)

        assertEquals(RecurrenceEnd.AfterOccurrences(5), display.end)
    }

    @Test
    fun end_nonPositiveCount_fallsThrough() {
        val display = rule(Frequency.Daily, occurrenceCount = 0).toDisplay(timing(), paris)

        assertEquals(RecurrenceEnd.Never, display.end)
    }

    @Test
    fun end_dateOnlyUntil_needsNoZone() {
        // A DATE UNTIL is already a calendar day: even a far-off device zone must not shift it.
        val until = RecurrenceUntil.DateOnly(LocalDate(2026, 12, 31))
        val display = rule(Frequency.Daily, until = until).toDisplay(timing(isAllDay = true), newYork)

        assertEquals(RecurrenceEnd.OnDate(LocalDate(2026, 12, 31)), display.end)
    }

    @Test
    fun end_floatingUntil_isTakenAsWallClock() {
        val until = RecurrenceUntil.Floating(LocalDateTime(2026, 12, 31, 23, 0))
        val display = rule(Frequency.Daily, until = until).toDisplay(timing(zone = null), newYork)

        assertEquals(RecurrenceEnd.OnDate(LocalDate(2026, 12, 31)), display.end)
    }

    @Test
    fun end_utcUntil_isReadInTheEventZone() {
        // 2026-12-31 23:00 UTC is already 2027-01-01 in Paris.
        val until = RecurrenceUntil.DateTimeUtc(LocalDateTime(2026, 12, 31, 23, 0).toInstant(TimeZone.UTC))
        val display = rule(Frequency.Daily, until = until).toDisplay(timing(zone = paris), newYork)

        assertEquals(RecurrenceEnd.OnDate(LocalDate(2027, 1, 1)), display.end)
    }

    @Test
    fun end_utcUntil_forFloatingEvent_usesDeviceZone() {
        val until = RecurrenceUntil.DateTimeUtc(LocalDateTime(2026, 12, 31, 23, 0).toInstant(TimeZone.UTC))
        val display = rule(Frequency.Daily, until = until).toDisplay(timing(zone = null), paris)

        assertEquals(RecurrenceEnd.OnDate(LocalDate(2027, 1, 1)), display.end)
    }

    // ---- Rule parts that must never be silently dropped -------------------------------------------

    @Test
    fun timeOfDayRules_areCustom_whateverTheFrequency() {
        // "Every day at 09:00 and 17:00" is a set expression, not a one-line sentence.
        assertEquals(RecurrencePattern.Custom, rule(Frequency.Daily, byHour = listOf(9, 17)).toDisplay(timing(), paris).pattern)
        assertEquals(RecurrencePattern.Custom, rule(Frequency.Weekly, byMinute = listOf(30)).toDisplay(timing(), paris).pattern)
        assertEquals(RecurrencePattern.Custom, rule(Frequency.Monthly, bySecond = listOf(0)).toDisplay(timing(), paris).pattern)
    }

    @Test
    fun yearScopedRules_areCustom_ratherThanAnIncompleteSentence() {
        // Without this, FREQ=YEARLY;BYWEEKNO=20 would read as a plain "every year on 6 August".
        assertEquals(
            RecurrencePattern.Custom,
            rule(Frequency.Yearly, byWeekNumber = listOf(20)).toDisplay(timing(), paris).pattern,
        )
        assertEquals(
            RecurrencePattern.Custom,
            rule(Frequency.Yearly, byYearDay = listOf(100)).toDisplay(timing(), paris).pattern,
        )
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun timing(isAllDay: Boolean = false, zone: TimeZone? = null) = EventTiming(
        start = anchor,
        end = anchor,
        startTimeZone = zone,
        endTimeZone = zone,
        isAllDay = isAllDay,
    )

    private fun rule(
        freq: Frequency,
        interval: Int = 1,
        occurrenceCount: Int? = null,
        until: RecurrenceUntil? = null,
        byDay: List<WeekDayNum> = emptyList(),
        byMonthDay: List<Int> = emptyList(),
        byMonth: List<Int> = emptyList(),
        byOccurrencePosition: List<Int> = emptyList(),
        weekStart: DayOfWeek? = null,
        byHour: List<Int> = emptyList(),
        byMinute: List<Int> = emptyList(),
        bySecond: List<Int> = emptyList(),
        byYearDay: List<Int> = emptyList(),
        byWeekNumber: List<Int> = emptyList(),
    ) = RecurrenceRule(
        freq = freq,
        interval = interval,
        occurrenceCount = occurrenceCount,
        until = until,
        byDay = byDay,
        byMonthDay = byMonthDay,
        byMonth = byMonth,
        byOccurrencePosition = byOccurrencePosition,
        weekStart = weekStart,
        byHour = byHour,
        byMinute = byMinute,
        bySecond = bySecond,
        byYearDay = byYearDay,
        byWeekNumber = byWeekNumber,
    )
}
