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
package com.infomaniak.multiplatform_calendar.core.localization

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.WeekDayNum
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RecurrenceTextFormatterTest {

    private val formatter = RecurrenceTextFormatter(KeyRecurrenceStringResolver)
    private val start = LocalDateTime(2026, 9, 30, 18, 30)

    @Test
    fun frequency_intervalOne_selectsTheExpectedResourceForEveryFrequency() = runTest {
        val expectations = mapOf(
            Frequency.Secondly to "recurrence_every_second",
            Frequency.Minutely to "recurrence_every_minute",
            Frequency.Hourly to "recurrence_every_hour",
            Frequency.Daily to "recurrence_every_day",
            Frequency.Weekly to "recurrence_every_week | recurrence_weekday_wednesday",
            Frequency.Monthly to "recurrence_every_month | recurrence_on_month_days(recurrence_month_day(30))",
            Frequency.Yearly to "recurrence_every_year | recurrence_on_annual_date(recurrence_date_month_september, 30)",
        )

        expectations.forEach { (frequency, expected) ->
            assertEquals(expected, format(RecurrenceRule(freq = frequency)), "for $frequency")
        }
    }

    @Test
    fun frequency_intervalGreaterThanOne_selectsTheExpectedPluralForEveryFrequency() = runTest {
        val expectations = mapOf(
            Frequency.Secondly to "recurrence_every_n_seconds[2](2)",
            Frequency.Minutely to "recurrence_every_n_minutes[2](2)",
            Frequency.Hourly to "recurrence_every_n_hours[2](2)",
            Frequency.Daily to "recurrence_every_n_days[2](2)",
            Frequency.Weekly to "recurrence_every_n_weeks[2](2) | recurrence_weekday_wednesday",
            Frequency.Monthly to "recurrence_every_n_months[2](2) | recurrence_on_month_days(recurrence_month_day(30))",
            Frequency.Yearly to "recurrence_every_n_years[2](2) | recurrence_on_annual_date(recurrence_date_month_september, 30)",
        )

        expectations.forEach { (frequency, expected) ->
            assertEquals(expected, format(RecurrenceRule(freq = frequency, interval = 2)), "for $frequency")
        }
    }

    @Test
    fun weekly_withoutByDay_inheritsWeekdayFromDtStart() = runTest {
        assertEquals(
            "recurrence_every_week | recurrence_weekday_wednesday",
            format(RecurrenceRule(freq = Frequency.Weekly)),
        )
    }

    @Test
    fun monthly_withoutDaySelector_inheritsMonthDayFromDtStart() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_on_month_days(recurrence_month_day(30))",
            format(RecurrenceRule(freq = Frequency.Monthly)),
        )
    }

    @Test
    fun yearly_withoutDateSelector_inheritsMonthAndDayFromDtStart() = runTest {
        assertEquals(
            "recurrence_every_year | recurrence_on_annual_date(recurrence_date_month_september, 30)",
            format(RecurrenceRule(freq = Frequency.Yearly)),
        )
    }

    @Test
    fun yearly_withByMonthAndNoDaySelector_inheritsOnlyMonthDayFromDtStart() = runTest {
        assertEquals(
            "recurrence_every_year | recurrence_month_january & recurrence_month_june | " +
                    "recurrence_on_month_days(recurrence_month_day(30))",
            format(RecurrenceRule(freq = Frequency.Yearly, byMonth = listOf(6, 1))),
        )
    }

    @Test
    fun byMonth_isSortedAndDeduplicated() = runTest {
        assertEquals(
            "recurrence_every_year | recurrence_month_january, recurrence_month_june & recurrence_month_september | " +
                    "recurrence_on_month_days(recurrence_month_day(30))",
            format(RecurrenceRule(freq = Frequency.Yearly, byMonth = listOf(9, 1, 6, 1))),
        )
    }

    @Test
    fun byMonthDay_supportsPositiveLastAndNegativePositions() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_on_month_days(" +
                    "recurrence_month_day(1), recurrence_month_day_last & recurrence_month_day_from_end(2))",
            format(RecurrenceRule(freq = Frequency.Monthly, byMonthDay = listOf(-1, 1, -2))),
        )
    }

    @Test
    fun byDay_supportsPositiveNegativeAndGenericOrdinals() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_ordinal_weekday_first(recurrence_weekday_name_monday), " +
                    "recurrence_ordinal_weekday_position(recurrence_weekday_name_tuesday, 6) & " +
                    "recurrence_ordinal_weekday_last(recurrence_weekday_name_friday)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(
                        WeekDayNum(ordinal = -1, dayOfWeek = DayOfWeek.FRIDAY),
                        WeekDayNum(ordinal = 6, dayOfWeek = DayOfWeek.TUESDAY),
                        WeekDayNum(ordinal = 1, dayOfWeek = DayOfWeek.MONDAY),
                    ),
                ),
            ),
        )
    }

    @Test
    fun weekStart_changesWeekdayOrderingAndIsAlsoDescribed() = runTest {
        assertEquals(
            "recurrence_every_week | recurrence_weekday_sunday, recurrence_weekday_monday & recurrence_weekday_friday | " +
                    "recurrence_week_start(recurrence_weekday_name_sunday)",
            format(
                RecurrenceRule(
                    freq = Frequency.Weekly,
                    byDay = listOf(
                        WeekDayNum(dayOfWeek = DayOfWeek.MONDAY),
                        WeekDayNum(dayOfWeek = DayOfWeek.FRIDAY),
                        WeekDayNum(dayOfWeek = DayOfWeek.SUNDAY),
                    ),
                    weekStart = DayOfWeek.SUNDAY,
                ),
            ),
        )
    }

    @Test
    fun byYearDayAndByWeekNumber_supportNegativePositions() = runTest {
        assertEquals(
            "recurrence_every_year | recurrence_in_week_numbers(recurrence_week_number(1) & recurrence_week_number_last) | " +
                    "recurrence_on_year_days(recurrence_year_day(100) & recurrence_year_day_last) | recurrence_weekday_wednesday",
            format(
                RecurrenceRule(
                    freq = Frequency.Yearly,
                    byYearDay = listOf(-1, 100),
                    byWeekNumber = listOf(-1, 1),
                ),
            ),
        )
    }

    @Test
    fun timeSelectors_areSortedAndDeduplicated() = runTest {
        assertEquals(
            "recurrence_every_day | recurrence_at_hours(8 & 17) | recurrence_at_minutes(15 & 45) | " +
                    "recurrence_at_seconds(0 & 30)",
            format(
                RecurrenceRule(
                    freq = Frequency.Daily,
                    byHour = listOf(17, 8, 17),
                    byMinute = listOf(45, 15),
                    bySecond = listOf(30, 0),
                ),
            ),
        )
    }

    @Test
    fun count_isRenderedWhenUntilIsAbsent() = runTest {
        assertEquals(
            "recurrence_every_n_weeks[2](2) | recurrence_weekday_wednesday | recurrence_for_occurrences[5](5)",
            format(RecurrenceRule(freq = Frequency.Weekly, interval = 2, occurrenceCount = 5)),
        )
    }

    @Test
    fun dateOnlyUntil_keepsItsDateDomain() = runTest {
        assertEquals(
            "recurrence_every_day | recurrence_until(recurrence_date(recurrence_date_month_december, 31, 2028))",
            format(
                RecurrenceRule(
                    freq = Frequency.Daily,
                    until = RecurrenceUntil.DateOnly(LocalDate(2028, 12, 31)),
                ),
                timeZone = TimeZone.of("Europe/Zurich"),
            ),
        )
    }

    @Test
    fun floatingUntil_keepsItsLocalCalendarDate() = runTest {
        assertEquals(
            "recurrence_every_day | recurrence_until(recurrence_date(recurrence_date_month_december, 31, 2028))",
            format(
                RecurrenceRule(
                    freq = Frequency.Daily,
                    until = RecurrenceUntil.Floating(LocalDateTime(2028, 12, 31, 23, 59, 59)),
                ),
                timeZone = TimeZone.of("Europe/Zurich"),
            ),
        )
    }

    @Test
    fun utcUntil_isPresentedInTheEventTimeZone() = runTest {
        assertEquals(
            "recurrence_every_day | recurrence_until(recurrence_date(recurrence_date_month_january, 1, 2029))",
            format(
                RecurrenceRule(
                    freq = Frequency.Daily,
                    until = RecurrenceUntil.DateTimeUtc(Instant.parse("2028-12-31T23:59:59Z")),
                ),
                timeZone = TimeZone.of("Europe/Zurich"),
            ),
        )
    }

    @Test
    fun until_takesPrecedenceOverCountForDefensiveFormatting() = runTest {
        assertEquals(
            "recurrence_every_day | recurrence_until(recurrence_date(recurrence_date_month_december, 31, 2028))",
            format(
                RecurrenceRule(
                    freq = Frequency.Daily,
                    occurrenceCount = 10,
                    until = RecurrenceUntil.DateOnly(LocalDate(2028, 12, 31)),
                ),
            ),
        )
    }

    @Test
    fun monthlySetPos_lastWithOneWeekday_becomesLastNamedWeekday() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_ordinal_weekday_last(recurrence_weekday_name_monday)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.MONDAY)),
                    byOccurrencePosition = listOf(-1),
                ),
            ),
        )
    }

    @Test
    fun monthlySetPos_lastWithSeveralWeekdays_keepsTheExplicitCandidateDays() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_last_matching_day(" +
                    "recurrence_weekday_name_monday, recurrence_weekday_name_tuesday, " +
                    "recurrence_weekday_name_wednesday, recurrence_weekday_name_thursday & recurrence_weekday_name_friday)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = WEEKDAYS.map { WeekDayNum(dayOfWeek = it) },
                    byOccurrencePosition = listOf(-1),
                ),
            ),
        )
    }

    @Test
    fun monthlySetPos_firstWithSeveralWeekdays_keepsTheExplicitCandidateDays() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_first_matching_day(" +
                    "recurrence_weekday_name_monday, recurrence_weekday_name_tuesday & recurrence_weekday_name_friday)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(
                        WeekDayNum(dayOfWeek = DayOfWeek.FRIDAY),
                        WeekDayNum(dayOfWeek = DayOfWeek.MONDAY),
                        WeekDayNum(dayOfWeek = DayOfWeek.TUESDAY),
                    ),
                    byOccurrencePosition = listOf(1),
                ),
            ),
        )
    }

    @Test
    fun monthlySetPos_withByMonth_stillUsesTheSafeDayShortcut() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_month_january & recurrence_month_june | " +
                    "recurrence_last_matching_day(recurrence_weekday_name_monday & recurrence_weekday_name_friday)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byMonth = listOf(6, 1),
                    byDay = listOf(
                        WeekDayNum(dayOfWeek = DayOfWeek.MONDAY),
                        WeekDayNum(dayOfWeek = DayOfWeek.FRIDAY),
                    ),
                    byOccurrencePosition = listOf(-1),
                ),
            ),
        )
    }

    @Test
    fun monthlySetPos_withTimeSelectors_doesNotPretendToSelectOnlyADay() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_weekday_monday | recurrence_at_hours(9 & 17) | " +
                    "recurrence_using_set_positions(recurrence_set_position_last)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.MONDAY)),
                    byHour = listOf(9, 17),
                    byOccurrencePosition = listOf(-1),
                ),
            ),
        )
    }

    @Test
    fun monthlySetPos_withAnotherDateSelector_fallsBackToGenericPositionText() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_on_month_days(recurrence_month_day(10)) | recurrence_weekday_monday | " +
                    "recurrence_using_set_positions(recurrence_set_position_last)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.MONDAY)),
                    byMonthDay = listOf(10),
                    byOccurrencePosition = listOf(-1),
                ),
            ),
        )
    }

    @Test
    fun setPos_otherThanFirstOrLastWithSeveralDays_isKeptGeneric() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_weekday_monday & recurrence_weekday_friday | " +
                    "recurrence_using_set_positions(recurrence_set_position(2))",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(
                        WeekDayNum(dayOfWeek = DayOfWeek.MONDAY),
                        WeekDayNum(dayOfWeek = DayOfWeek.FRIDAY),
                    ),
                    byOccurrencePosition = listOf(2),
                ),
            ),
        )
    }

    @Test
    fun multipleSetPositions_areSortedAndKeptGeneric() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_weekday_monday | " +
                    "recurrence_using_set_positions(recurrence_set_position_first & recurrence_set_position_last)",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.MONDAY)),
                    byOccurrencePosition = listOf(-1, 1),
                ),
            ),
        )
    }

    @Test
    fun multipleNegativeSetPositions_areSortedByDistanceFromEnd() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_weekday_monday | " +
                    "recurrence_using_set_positions(recurrence_set_position_first, " +
                    "recurrence_set_position_last & recurrence_set_position_from_end(2))",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = listOf(WeekDayNum(dayOfWeek = DayOfWeek.MONDAY)),
                    byOccurrencePosition = listOf(-2, 1, -1),
                ),
            ),
        )
    }

    @Test
    fun referenceRule_preservesByMonthByDayBySetPosAndUntilSemantics() = runTest {
        assertEquals(
            "recurrence_every_month | recurrence_month_january, recurrence_month_february, recurrence_month_march, " +
                    "recurrence_month_april, recurrence_month_may, recurrence_month_june, recurrence_month_september, " +
                    "recurrence_month_october, recurrence_month_november & recurrence_month_december | " +
                    "recurrence_last_matching_day(recurrence_weekday_name_monday, recurrence_weekday_name_tuesday, " +
                    "recurrence_weekday_name_wednesday, recurrence_weekday_name_thursday & recurrence_weekday_name_friday) | " +
                    "recurrence_until(recurrence_date(recurrence_date_month_january, 1, 2029))",
            format(
                RecurrenceRule(
                    freq = Frequency.Monthly,
                    byDay = WEEKDAYS.map { WeekDayNum(dayOfWeek = it) },
                    byOccurrencePosition = listOf(-1),
                    byMonth = listOf(1, 2, 3, 4, 5, 6, 9, 10, 11, 12),
                    until = RecurrenceUntil.DateTimeUtc(Instant.parse("2028-12-31T23:59:59Z")),
                ),
                timeZone = TimeZone.of("Europe/Zurich"),
            ),
        )
    }

    @Test
    fun invalidMonth_failsInsteadOfSilentlyProducingWrongText() = runTest {
        assertFailsWith<IllegalStateException> {
            format(RecurrenceRule(freq = Frequency.Yearly, byMonth = listOf(13)))
        }
    }

    private suspend fun format(
        rule: RecurrenceRule,
        timeZone: TimeZone? = null,
    ): String = formatter.format(rule, start, timeZone)

    private companion object {
        val WEEKDAYS = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}

/**
 * Deterministic test resolver: the formatter tests the resource selected and the semantic arguments
 * without depending on the host locale or Compose's platform resource environment.
 */
private object KeyRecurrenceStringResolver : RecurrenceStringResolver {

    override suspend fun string(resource: StringResource, vararg formatArgs: Any): String = when (resource.key) {
        "recurrence_clause_join" -> "${formatArgs[0]} | ${formatArgs[1]}"
        "recurrence_list_two" -> "${formatArgs[0]} & ${formatArgs[1]}"
        "recurrence_list_append" -> "${formatArgs[0]}, ${formatArgs[1]}"
        "recurrence_list_last" -> "${formatArgs[0]} & ${formatArgs[1]}"
        "recurrence_number" -> formatArgs.single().toString()
        else -> resource.key.withArguments(formatArgs)
    }

    override suspend fun plural(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = "${resource.key}[$quantity]" + if (formatArgs.isEmpty()) "" else "(${formatArgs.joinToString()})"

    private fun String.withArguments(args: Array<out Any>): String =
        if (args.isEmpty()) this else "$this(${args.joinToString()})"
}
