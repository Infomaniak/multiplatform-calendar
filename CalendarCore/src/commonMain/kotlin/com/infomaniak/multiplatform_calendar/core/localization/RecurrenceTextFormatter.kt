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
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.RecurrenceCandidateSet.inheritsDtStartWeekdayForWeekNumber
import com.infomaniak.multiplatform_calendar.core.extensions.mapCancellable
import com.infomaniak.multiplatform_calendar.resources.Res
import com.infomaniak.multiplatform_calendar.resources.recurrence_at_hour
import com.infomaniak.multiplatform_calendar.resources.recurrence_at_hours
import com.infomaniak.multiplatform_calendar.resources.recurrence_at_minute
import com.infomaniak.multiplatform_calendar.resources.recurrence_at_minutes
import com.infomaniak.multiplatform_calendar.resources.recurrence_at_second
import com.infomaniak.multiplatform_calendar.resources.recurrence_at_seconds
import com.infomaniak.multiplatform_calendar.resources.recurrence_clause_join
import com.infomaniak.multiplatform_calendar.resources.recurrence_date
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_april
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_august
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_december
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_february
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_january
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_july
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_june
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_march
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_may
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_november
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_october
import com.infomaniak.multiplatform_calendar.resources.recurrence_date_month_september
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_day
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_hour
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_minute
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_month
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_n_days
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_n_hours
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_n_minutes
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_n_months
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_n_seconds
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_n_weeks
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_n_years
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_second
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_week
import com.infomaniak.multiplatform_calendar.resources.recurrence_every_year
import com.infomaniak.multiplatform_calendar.resources.recurrence_first_matching_day
import com.infomaniak.multiplatform_calendar.resources.recurrence_for_occurrences
import com.infomaniak.multiplatform_calendar.resources.recurrence_in_week_numbers
import com.infomaniak.multiplatform_calendar.resources.recurrence_last_matching_day
import com.infomaniak.multiplatform_calendar.resources.recurrence_list_append
import com.infomaniak.multiplatform_calendar.resources.recurrence_list_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_list_two
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_april
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_august
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_day
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_day_from_end
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_day_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_december
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_february
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_january
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_july
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_june
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_march
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_may
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_november
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_october
import com.infomaniak.multiplatform_calendar.resources.recurrence_month_september
import com.infomaniak.multiplatform_calendar.resources.recurrence_number
import com.infomaniak.multiplatform_calendar.resources.recurrence_on_annual_date
import com.infomaniak.multiplatform_calendar.resources.recurrence_on_month_days
import com.infomaniak.multiplatform_calendar.resources.recurrence_on_year_days
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_fifth
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_fifth_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_first
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_fourth
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_fourth_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_position
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_position_from_end
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_second
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_second_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_third
import com.infomaniak.multiplatform_calendar.resources.recurrence_ordinal_weekday_third_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_set_position
import com.infomaniak.multiplatform_calendar.resources.recurrence_set_position_first
import com.infomaniak.multiplatform_calendar.resources.recurrence_set_position_from_end
import com.infomaniak.multiplatform_calendar.resources.recurrence_set_position_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_until
import com.infomaniak.multiplatform_calendar.resources.recurrence_using_set_positions
import com.infomaniak.multiplatform_calendar.resources.recurrence_week_number
import com.infomaniak.multiplatform_calendar.resources.recurrence_week_number_from_end
import com.infomaniak.multiplatform_calendar.resources.recurrence_week_number_last
import com.infomaniak.multiplatform_calendar.resources.recurrence_week_start
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_friday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_monday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_name_friday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_name_monday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_name_saturday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_name_sunday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_name_thursday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_name_tuesday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_name_wednesday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_saturday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_sunday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_thursday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_tuesday
import com.infomaniak.multiplatform_calendar.resources.recurrence_weekday_wednesday
import com.infomaniak.multiplatform_calendar.resources.recurrence_year_day
import com.infomaniak.multiplatform_calendar.resources.recurrence_year_day_from_end
import com.infomaniak.multiplatform_calendar.resources.recurrence_year_day_last
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

internal class RecurrenceTextFormatter(
    private val strings: RecurrenceStringResolver = ComposeRecurrenceStringResolver,
) {

    suspend fun format(
        rule: RecurrenceRule,
        start: LocalDateTime,
        timeZone: TimeZone?,
    ): String {
        currentCoroutineContext().ensureActive()
        val clauses = mutableListOf(formatFrequency(rule))
        val setPositionShortcut = clauses.appendDateClauses(rule, start)

        clauses.appendTimeClauses(rule)
        clauses.appendSetPositionClause(rule, setPositionShortcut)
        clauses.appendWeekStartAndEndClauses(rule, timeZone)

        return joinClauses(clauses)
    }

    private suspend fun MutableList<String>.appendDateClauses(rule: RecurrenceRule, start: LocalDateTime): String? {
        if (rule.byMonth.isNotEmpty()) this += formatMonths(rule.byMonth)
        if (rule.byWeekNumber.isNotEmpty()) this += formatWeekNumbers(rule.byWeekNumber)
        if (rule.byYearDay.isNotEmpty()) this += formatYearDays(rule.byYearDay)

        val setPositionShortcut = formatMonthlySetPositionShortcut(rule)
        when {
            rule.byMonthDay.isEmpty() -> formatImplicitMonthDay(rule, start)?.let(this::add)
            else -> this += formatMonthDays(rule.byMonthDay)
        }

        when {
            setPositionShortcut != null -> this += setPositionShortcut
            rule.byDay.isNotEmpty() -> this += formatWeekDays(rule.byDay, rule.weekStart)
            else -> formatImplicitWeekDay(rule, start)?.let(this::add)
        }

        if (shouldUseImplicitAnnualDate(rule)) {
            this += formatAnnualDate(start.date)
        }

        return setPositionShortcut
    }

    private suspend fun MutableList<String>.appendTimeClauses(rule: RecurrenceRule) {
        if (rule.byHour.isNotEmpty()) this += formatHours(rule.byHour)
        if (rule.byMinute.isNotEmpty()) this += formatMinutes(rule.byMinute)
        if (rule.bySecond.isNotEmpty()) this += formatSeconds(rule.bySecond)
    }

    private suspend fun MutableList<String>.appendSetPositionClause(rule: RecurrenceRule, setPositionShortcut: String?) {
        if (setPositionShortcut == null && rule.byOccurrencePosition.isNotEmpty()) {
            this += formatSetPositions(rule.byOccurrencePosition)
        }
    }

    private suspend fun MutableList<String>.appendWeekStartAndEndClauses(rule: RecurrenceRule, timeZone: TimeZone?) {
        rule.weekStart?.let { this += strings.string(Res.string.recurrence_week_start, weekDayName(it)) }

        when {
            rule.until != null -> this += formatUntil(rule.until, timeZone)
            rule.occurrenceCount != null -> this += strings.plural(
                Res.plurals.recurrence_for_occurrences,
                rule.occurrenceCount,
                rule.occurrenceCount,
            )
        }
    }

    private suspend fun formatFrequency(rule: RecurrenceRule): String {
        if (rule.interval == 1) {
            return strings.string(
                when (rule.freq) {
                    Frequency.Secondly -> Res.string.recurrence_every_second
                    Frequency.Minutely -> Res.string.recurrence_every_minute
                    Frequency.Hourly -> Res.string.recurrence_every_hour
                    Frequency.Daily -> Res.string.recurrence_every_day
                    Frequency.Weekly -> Res.string.recurrence_every_week
                    Frequency.Monthly -> Res.string.recurrence_every_month
                    Frequency.Yearly -> Res.string.recurrence_every_year
                },
            )
        }

        val resource = when (rule.freq) {
            Frequency.Secondly -> Res.plurals.recurrence_every_n_seconds
            Frequency.Minutely -> Res.plurals.recurrence_every_n_minutes
            Frequency.Hourly -> Res.plurals.recurrence_every_n_hours
            Frequency.Daily -> Res.plurals.recurrence_every_n_days
            Frequency.Weekly -> Res.plurals.recurrence_every_n_weeks
            Frequency.Monthly -> Res.plurals.recurrence_every_n_months
            Frequency.Yearly -> Res.plurals.recurrence_every_n_years
        }
        return strings.plural(resource, rule.interval, rule.interval)
    }

    private suspend fun formatMonths(months: List<Int>): String = localizedList(
        months.distinct().sorted().mapCancellable { recurrenceMonthText(it) },
    )

    private suspend fun formatMonthDays(days: List<Int>): String {
        val values = days.distinct().sortedRecurrencePositions().mapCancellable { value ->
            when {
                value == -1 -> strings.string(Res.string.recurrence_month_day_last)
                value < 0 -> strings.string(Res.string.recurrence_month_day_from_end, -value)
                else -> strings.string(Res.string.recurrence_month_day, value)
            }
        }
        return strings.string(Res.string.recurrence_on_month_days, localizedList(values))
    }

    private suspend fun formatWeekDays(days: List<WeekDayNum>, weekStart: DayOfWeek?): String = localizedList(
        days.distinct().sortedWeekDayNums(weekStart).mapCancellable { day ->
            day.ordinal?.let { ordinalWeekDayText(day.dayOfWeek, it) }
                ?: recurrenceWeekDayText(day.dayOfWeek)
        },
    )

    /**
     * Human-friendly MONTHLY BYSETPOS rewrites are deliberately conservative.
     *
     * Safe examples:
     * - BYDAY=MO;BYSETPOS=-1 -> last Monday.
     * - BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1 -> last matching day (Monday...Friday).
     *
     * If time selectors or another date selector also participates in the candidate set,
     * BYSETPOS is left explicit because calling it a "last day" would be semantically wrong.
     */
    private suspend fun formatMonthlySetPositionShortcut(rule: RecurrenceRule): String? {
        if (rule.freq != Frequency.Monthly) return null
        if (rule.byOccurrencePosition.size != 1) return null
        if (rule.byDay.isEmpty() || rule.byDay.any { it.ordinal != null }) return null
        if (
            rule.byMonthDay.isNotEmpty() ||
            rule.byYearDay.isNotEmpty() ||
            rule.byWeekNumber.isNotEmpty() ||
            rule.byHour.isNotEmpty() ||
            rule.byMinute.isNotEmpty() ||
            rule.bySecond.isNotEmpty()
        ) return null

        val position = rule.byOccurrencePosition.single()
        if (position == 0) return null

        val days = rule.byDay.map { it.dayOfWeek }.distinct()
        if (days.size == 1 && position in -5..5) {
            return ordinalWeekDayText(days.single(), position)
        }

        if (days.size > 1 && position in setOf(1, -1)) {
            val names = localizedList(days.sortedDaysOfWeek(rule.weekStart).mapCancellable { weekDayName(it) })
            return strings.string(
                if (position == 1) Res.string.recurrence_first_matching_day
                else Res.string.recurrence_last_matching_day,
                names,
            )
        }
        return null
    }

    private suspend fun ordinalWeekDayText(day: DayOfWeek, ordinal: Int): String {
        val name = weekDayName(day)
        val resource: StringResource = when (ordinal) {
            1 -> Res.string.recurrence_ordinal_weekday_first
            2 -> Res.string.recurrence_ordinal_weekday_second
            3 -> Res.string.recurrence_ordinal_weekday_third
            4 -> Res.string.recurrence_ordinal_weekday_fourth
            5 -> Res.string.recurrence_ordinal_weekday_fifth
            -1 -> Res.string.recurrence_ordinal_weekday_last
            -2 -> Res.string.recurrence_ordinal_weekday_second_last
            -3 -> Res.string.recurrence_ordinal_weekday_third_last
            -4 -> Res.string.recurrence_ordinal_weekday_fourth_last
            -5 -> Res.string.recurrence_ordinal_weekday_fifth_last
            else -> return if (ordinal > 0) {
                strings.string(Res.string.recurrence_ordinal_weekday_position, name, ordinal)
            } else {
                strings.string(Res.string.recurrence_ordinal_weekday_position_from_end, name, -ordinal)
            }
        }
        return strings.string(resource, name)
    }

    private suspend fun formatSetPositions(positions: List<Int>): String {
        val values = positions.distinct().sortedRecurrencePositions().mapCancellable { value ->
            when {
                value == 1 -> strings.string(Res.string.recurrence_set_position_first)
                value == -1 -> strings.string(Res.string.recurrence_set_position_last)
                value > 0 -> strings.string(Res.string.recurrence_set_position, value)
                else -> strings.string(Res.string.recurrence_set_position_from_end, -value)
            }
        }
        return strings.string(Res.string.recurrence_using_set_positions, localizedList(values))
    }

    private suspend fun formatYearDays(days: List<Int>): String {
        val values = days.distinct().sortedRecurrencePositions().mapCancellable { value ->
            when {
                value == -1 -> strings.string(Res.string.recurrence_year_day_last)
                value < 0 -> strings.string(Res.string.recurrence_year_day_from_end, -value)
                else -> strings.string(Res.string.recurrence_year_day, value)
            }
        }
        return strings.string(Res.string.recurrence_on_year_days, localizedList(values))
    }

    private suspend fun formatWeekNumbers(weeks: List<Int>): String {
        val values = weeks.distinct().sortedRecurrencePositions().mapCancellable { value ->
            when {
                value == -1 -> strings.string(Res.string.recurrence_week_number_last)
                value < 0 -> strings.string(Res.string.recurrence_week_number_from_end, -value)
                else -> strings.string(Res.string.recurrence_week_number, value)
            }
        }
        return strings.string(Res.string.recurrence_in_week_numbers, localizedList(values))
    }

    private suspend fun formatHours(hours: List<Int>): String {
        val values = hours.distinct().sorted()
        return if (values.size == 1) strings.string(Res.string.recurrence_at_hour, values.single())
        else strings.string(Res.string.recurrence_at_hours, localizedNumberList(values))
    }

    private suspend fun formatMinutes(minutes: List<Int>): String {
        val values = minutes.distinct().sorted()
        return if (values.size == 1) strings.string(Res.string.recurrence_at_minute, values.single())
        else strings.string(Res.string.recurrence_at_minutes, localizedNumberList(values))
    }

    private suspend fun formatSeconds(seconds: List<Int>): String {
        val values = seconds.distinct().sorted()
        return if (values.size == 1) strings.string(Res.string.recurrence_at_second, values.single())
        else strings.string(Res.string.recurrence_at_seconds, localizedNumberList(values))
    }

    private suspend fun formatImplicitMonthDay(rule: RecurrenceRule, start: LocalDateTime): String? {
        val hasDaySelector = rule.byMonthDay.isNotEmpty() || rule.byDay.isNotEmpty() ||
                rule.byYearDay.isNotEmpty() || rule.byWeekNumber.isNotEmpty()
        if (hasDaySelector) return null

        return when (rule.freq) {
            Frequency.Monthly -> formatMonthDays(listOf(start.day))
            Frequency.Yearly -> if (rule.byMonth.isNotEmpty()) formatMonthDays(listOf(start.day)) else null
            else -> null
        }
    }

    private suspend fun formatImplicitWeekDay(rule: RecurrenceRule, start: LocalDateTime): String? = when (rule.freq) {
        Frequency.Weekly if rule.byMonthDay.isEmpty()
                && rule.byYearDay.isEmpty()
                && rule.byWeekNumber.isEmpty() -> recurrenceWeekDayText(start.dayOfWeek)
        Frequency.Yearly if rule.inheritsDtStartWeekdayForWeekNumber() -> recurrenceWeekDayText(start.dayOfWeek)
        else -> null
    }

    private fun shouldUseImplicitAnnualDate(rule: RecurrenceRule): Boolean =
        rule.freq == Frequency.Yearly &&
                rule.byMonth.isEmpty() && rule.byMonthDay.isEmpty() && rule.byDay.isEmpty() &&
                rule.byYearDay.isEmpty() && rule.byWeekNumber.isEmpty()

    private suspend fun formatAnnualDate(date: LocalDate): String = strings.string(
        Res.string.recurrence_on_annual_date,
        dateMonthName(date.month.ordinal + 1),
        date.day,
    )

    private suspend fun formatUntil(until: RecurrenceUntil, timeZone: TimeZone?): String {
        val date = when (until) {
            is RecurrenceUntil.DateOnly -> until.date
            is RecurrenceUntil.Floating -> until.dateTime.date
            is RecurrenceUntil.DateTimeUtc -> until.instant.toLocalDateTime(timeZone ?: TimeZone.UTC).date
        }
        return strings.string(Res.string.recurrence_until, formatDate(date))
    }

    private suspend fun formatDate(date: LocalDate): String = strings.string(
        Res.string.recurrence_date,
        dateMonthName(date.month.ordinal + 1),
        date.day,
        date.year,
    )

    private suspend fun recurrenceWeekDayText(day: DayOfWeek): String = strings.string(
        when (day) {
            DayOfWeek.MONDAY -> Res.string.recurrence_weekday_monday
            DayOfWeek.TUESDAY -> Res.string.recurrence_weekday_tuesday
            DayOfWeek.WEDNESDAY -> Res.string.recurrence_weekday_wednesday
            DayOfWeek.THURSDAY -> Res.string.recurrence_weekday_thursday
            DayOfWeek.FRIDAY -> Res.string.recurrence_weekday_friday
            DayOfWeek.SATURDAY -> Res.string.recurrence_weekday_saturday
            DayOfWeek.SUNDAY -> Res.string.recurrence_weekday_sunday
        },
    )

    private suspend fun weekDayName(day: DayOfWeek): String = strings.string(
        when (day) {
            DayOfWeek.MONDAY -> Res.string.recurrence_weekday_name_monday
            DayOfWeek.TUESDAY -> Res.string.recurrence_weekday_name_tuesday
            DayOfWeek.WEDNESDAY -> Res.string.recurrence_weekday_name_wednesday
            DayOfWeek.THURSDAY -> Res.string.recurrence_weekday_name_thursday
            DayOfWeek.FRIDAY -> Res.string.recurrence_weekday_name_friday
            DayOfWeek.SATURDAY -> Res.string.recurrence_weekday_name_saturday
            DayOfWeek.SUNDAY -> Res.string.recurrence_weekday_name_sunday
        },
    )

    private suspend fun recurrenceMonthText(month: Int): String = strings.string(monthResource(month, date = false))
    private suspend fun dateMonthName(month: Int): String = strings.string(monthResource(month, date = true))

    private fun monthResource(month: Int, date: Boolean): StringResource =
        if (date) dateMonthResource(month) else recurrenceMonthResource(month)

    private fun recurrenceMonthResource(month: Int): StringResource = when (month) {
        1 -> Res.string.recurrence_month_january
        2 -> Res.string.recurrence_month_february
        3 -> Res.string.recurrence_month_march
        4 -> Res.string.recurrence_month_april
        5 -> Res.string.recurrence_month_may
        6 -> Res.string.recurrence_month_june
        7 -> Res.string.recurrence_month_july
        8 -> Res.string.recurrence_month_august
        9 -> Res.string.recurrence_month_september
        10 -> Res.string.recurrence_month_october
        11 -> Res.string.recurrence_month_november
        12 -> Res.string.recurrence_month_december
        else -> error("Invalid RFC 5545 month: $month")
    }

    private fun dateMonthResource(month: Int): StringResource = when (month) {
        1 -> Res.string.recurrence_date_month_january
        2 -> Res.string.recurrence_date_month_february
        3 -> Res.string.recurrence_date_month_march
        4 -> Res.string.recurrence_date_month_april
        5 -> Res.string.recurrence_date_month_may
        6 -> Res.string.recurrence_date_month_june
        7 -> Res.string.recurrence_date_month_july
        8 -> Res.string.recurrence_date_month_august
        9 -> Res.string.recurrence_date_month_september
        10 -> Res.string.recurrence_date_month_october
        11 -> Res.string.recurrence_date_month_november
        12 -> Res.string.recurrence_date_month_december
        else -> error("Invalid RFC 5545 month: $month")
    }

    private suspend fun localizedNumberList(values: List<Int>): String = localizedList(
        values.mapCancellable { strings.string(Res.string.recurrence_number, it) },
    )

    private suspend fun localizedList(items: List<String>): String {
        require(items.isNotEmpty())
        if (items.size == 1) return items.single()
        if (items.size == 2) return strings.string(Res.string.recurrence_list_two, items[0], items[1])

        var result = strings.string(Res.string.recurrence_list_append, items[0], items[1])
        for (index in 2 until items.lastIndex) {
            currentCoroutineContext().ensureActive()
            result = strings.string(Res.string.recurrence_list_append, result, items[index])
        }
        currentCoroutineContext().ensureActive()
        return strings.string(Res.string.recurrence_list_last, result, items.last())
    }

    private suspend fun joinClauses(clauses: List<String>): String = clauses.drop(1).fold(clauses.first()) { result, clause ->
        strings.string(Res.string.recurrence_clause_join, result, clause)
    }
}

private object ComposeRecurrenceStringResolver : RecurrenceStringResolver {
    override suspend fun string(resource: StringResource, vararg formatArgs: Any): String =
        getString(resource, *formatArgs)

    override suspend fun plural(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = getPluralString(resource, quantity, *formatArgs)
}

private fun List<Int>.sortedRecurrencePositions(): List<Int> = sortedWith(
    compareBy<Int>({ if (it > 0) 0 else 1 }, { if (it > 0) it else -it }),
)

private fun List<WeekDayNum>.sortedWeekDayNums(weekStart: DayOfWeek?): List<WeekDayNum> {
    val start = weekStart ?: DayOfWeek.MONDAY
    val days = DayOfWeek.entries
    val startIndex = days.indexOf(start)
    val order = days.drop(startIndex) + days.take(startIndex)
    return sortedWith(compareBy<WeekDayNum> { order.indexOf(it.dayOfWeek) }.thenBy { it.ordinal ?: 0 })
}

private fun List<DayOfWeek>.sortedDaysOfWeek(weekStart: DayOfWeek?): List<DayOfWeek> {
    val start = weekStart ?: DayOfWeek.MONDAY
    val days = DayOfWeek.entries
    val startIndex = days.indexOf(start)
    val order = days.drop(startIndex) + days.take(startIndex)
    return sortedBy(order::indexOf)
}
