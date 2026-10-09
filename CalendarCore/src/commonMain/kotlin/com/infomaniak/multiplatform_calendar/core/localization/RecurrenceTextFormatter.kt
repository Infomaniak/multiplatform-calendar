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
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.math.absoluteValue

internal class RecurrenceTextFormatter(
    private val strings: RecurrenceStringResolver = ComposeRecurrenceStringResolver,
) {

    suspend fun format(
        rule: RecurrenceRule,
        start: LocalDateTime,
        timeZone: TimeZone?,
    ): String {
        currentCoroutineContext().ensureActive()
        val setPositionShortcut = rule.monthlySetPositionShortcut()
        val clauses = mutableListOf(formatFrequency(rule))

        clauses.appendDateClauses(rule, start, setPositionShortcut)
        clauses.appendTimeClauses(rule)
        if (setPositionShortcut == null) clauses.appendSetPositionClause(rule)
        clauses.appendWeekStartAndEndClauses(rule, timeZone)

        return joinClauses(clauses)
    }

    private suspend fun MutableList<String>.appendDateClauses(
        rule: RecurrenceRule,
        start: LocalDateTime,
        setPositionShortcut: SetPositionShortcut?,
    ) {
        if (rule.byMonth.isNotEmpty()) this += formatMonths(rule.byMonth)
        if (rule.byWeekNumber.isNotEmpty()) this += formatWeekNumbers(rule.byWeekNumber)
        if (rule.byYearDay.isNotEmpty()) this += formatYearDays(rule.byYearDay)

        when {
            rule.byMonthDay.isEmpty() -> formatImplicitMonthDay(rule, start)?.let(this::add)
            else -> this += formatMonthDays(rule.byMonthDay)
        }

        when {
            setPositionShortcut != null -> this += formatSetPositionShortcut(setPositionShortcut)
            rule.byDay.isNotEmpty() -> this += formatWeekDays(rule.byDay, rule.weekStart)
            else -> formatImplicitWeekDay(rule, start)?.let(this::add)
        }

        if (shouldUseImplicitAnnualDate(rule)) {
            this += formatAnnualDate(start.date)
        }
    }

    private suspend fun MutableList<String>.appendTimeClauses(rule: RecurrenceRule) {
        if (rule.byHour.isNotEmpty()) this += formatHours(rule.byHour)
        if (rule.byMinute.isNotEmpty()) this += formatMinutes(rule.byMinute)
        if (rule.bySecond.isNotEmpty()) this += formatSeconds(rule.bySecond)
    }

    private suspend fun MutableList<String>.appendSetPositionClause(rule: RecurrenceRule) {
        if (rule.byOccurrencePosition.isNotEmpty()) this += formatSetPositions(rule.byOccurrencePosition)
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

    private suspend fun formatFrequency(rule: RecurrenceRule): String =
        if (rule.interval == 1) strings.string(rule.freq.everyResource)
        else strings.plural(rule.freq.everyNResource, rule.interval, rule.interval)

    private suspend fun formatMonths(months: List<Int>): String = localizedList(
        months.distinct().sorted().mapCancellable { strings.string(Month(it).recurrenceResource) },
    )

    private suspend fun formatMonthDays(days: List<Int>): String = formatPositions(
        positions = days,
        last = Res.string.recurrence_month_day_last,
        fromEnd = Res.string.recurrence_month_day_from_end,
        fromStart = Res.string.recurrence_month_day,
        list = Res.string.recurrence_on_month_days,
    )

    private suspend fun formatYearDays(days: List<Int>): String = formatPositions(
        positions = days,
        last = Res.string.recurrence_year_day_last,
        fromEnd = Res.string.recurrence_year_day_from_end,
        fromStart = Res.string.recurrence_year_day,
        list = Res.string.recurrence_on_year_days,
    )

    private suspend fun formatWeekNumbers(weeks: List<Int>): String = formatPositions(
        positions = weeks,
        last = Res.string.recurrence_week_number_last,
        fromEnd = Res.string.recurrence_week_number_from_end,
        fromStart = Res.string.recurrence_week_number,
        list = Res.string.recurrence_in_week_numbers,
    )

    private suspend fun formatPositions(
        positions: List<Int>,
        last: StringResource,
        fromEnd: StringResource,
        fromStart: StringResource,
        list: StringResource,
    ): String {
        val values = positions.distinct().sortedRecurrencePositions().mapCancellable { position ->
            when {
                position == LAST_POSITION -> strings.string(last)
                position.isFromEnd -> strings.string(fromEnd, position.absoluteValue)
                else -> strings.string(fromStart, position)
            }
        }
        return strings.string(list, localizedList(values))
    }

    private suspend fun formatWeekDays(days: List<WeekDayNum>, weekStart: DayOfWeek?): String = localizedList(
        days.distinct().sortedWeekDayNums(weekStart).mapCancellable { day ->
            day.ordinal?.let { ordinalWeekDayText(day.dayOfWeek, it) }
                ?: strings.string(day.dayOfWeek.recurrenceResource)
        },
    )

    private suspend fun formatSetPositionShortcut(shortcut: SetPositionShortcut): String = when (shortcut) {
        is SetPositionShortcut.WeekDay -> ordinalWeekDayText(shortcut.day, shortcut.position)
        is SetPositionShortcut.MatchingDays -> strings.string(
            if (shortcut.isFirst) Res.string.recurrence_first_matching_day else Res.string.recurrence_last_matching_day,
            localizedList(shortcut.days.mapCancellable { weekDayName(it) }),
        )
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
        val values = positions.distinct().sortedRecurrencePositions().mapCancellable { position ->
            when {
                position == FIRST_POSITION -> strings.string(Res.string.recurrence_set_position_first)
                position == LAST_POSITION -> strings.string(Res.string.recurrence_set_position_last)
                position.isFromEnd -> strings.string(Res.string.recurrence_set_position_from_end, position.absoluteValue)
                else -> strings.string(Res.string.recurrence_set_position, position)
            }
        }
        return strings.string(Res.string.recurrence_using_set_positions, localizedList(values))
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
                && rule.byWeekNumber.isEmpty() -> strings.string(start.dayOfWeek.recurrenceResource)
        Frequency.Yearly if rule.inheritsDtStartWeekdayForWeekNumber() -> strings.string(start.dayOfWeek.recurrenceResource)
        else -> null
    }

    private fun shouldUseImplicitAnnualDate(rule: RecurrenceRule): Boolean =
        rule.freq == Frequency.Yearly &&
                rule.byMonth.isEmpty() && rule.byMonthDay.isEmpty() && rule.byDay.isEmpty() &&
                rule.byYearDay.isEmpty() && rule.byWeekNumber.isEmpty()

    private suspend fun formatAnnualDate(date: LocalDate): String = strings.string(
        Res.string.recurrence_on_annual_date,
        strings.string(date.month.dateResource),
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
        strings.string(date.month.dateResource),
        date.day,
        date.year,
    )

    private suspend fun weekDayName(day: DayOfWeek): String = strings.string(day.nameResource)

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

private const val FIRST_POSITION = 1
private const val LAST_POSITION = -1
private const val MAX_WEEKDAY_ORDINAL = 5

private object ComposeRecurrenceStringResolver : RecurrenceStringResolver {
    override suspend fun string(resource: StringResource, vararg formatArgs: Any): String =
        getString(resource, *formatArgs)

    override suspend fun plural(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = getPluralString(resource, quantity, *formatArgs)
}

/** A MONTHLY BYSETPOS that reads naturally, like "last Monday", instead of an explicit set position. */
private sealed interface SetPositionShortcut {
    data class WeekDay(val day: DayOfWeek, val position: Int) : SetPositionShortcut
    data class MatchingDays(val days: List<DayOfWeek>, val isFirst: Boolean) : SetPositionShortcut
}

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
private fun RecurrenceRule.monthlySetPositionShortcut(): SetPositionShortcut? {
    if (freq != Frequency.Monthly) return null
    if (byOccurrencePosition.size != 1) return null
    if (byDay.isEmpty() || byDay.any { it.ordinal != null }) return null
    if (
        byMonthDay.isNotEmpty() ||
        byYearDay.isNotEmpty() ||
        byWeekNumber.isNotEmpty() ||
        byHour.isNotEmpty() ||
        byMinute.isNotEmpty() ||
        bySecond.isNotEmpty()
    ) return null

    val position = byOccurrencePosition.single()
    if (position == 0) return null

    val days = byDay.map { it.dayOfWeek }.distinct()
    return when {
        days.size == 1 && position.absoluteValue <= MAX_WEEKDAY_ORDINAL -> SetPositionShortcut.WeekDay(days.single(), position)
        days.size > 1 && (position == FIRST_POSITION || position == LAST_POSITION) -> SetPositionShortcut.MatchingDays(
            days = days.sortedDaysOfWeek(weekStart),
            isFirst = position == FIRST_POSITION,
        )
        else -> null
    }
}

private val Frequency.everyResource: StringResource
    get() = when (this) {
        Frequency.Secondly -> Res.string.recurrence_every_second
        Frequency.Minutely -> Res.string.recurrence_every_minute
        Frequency.Hourly -> Res.string.recurrence_every_hour
        Frequency.Daily -> Res.string.recurrence_every_day
        Frequency.Weekly -> Res.string.recurrence_every_week
        Frequency.Monthly -> Res.string.recurrence_every_month
        Frequency.Yearly -> Res.string.recurrence_every_year
    }

private val Frequency.everyNResource: PluralStringResource
    get() = when (this) {
        Frequency.Secondly -> Res.plurals.recurrence_every_n_seconds
        Frequency.Minutely -> Res.plurals.recurrence_every_n_minutes
        Frequency.Hourly -> Res.plurals.recurrence_every_n_hours
        Frequency.Daily -> Res.plurals.recurrence_every_n_days
        Frequency.Weekly -> Res.plurals.recurrence_every_n_weeks
        Frequency.Monthly -> Res.plurals.recurrence_every_n_months
        Frequency.Yearly -> Res.plurals.recurrence_every_n_years
    }

private val DayOfWeek.recurrenceResource: StringResource
    get() = when (this) {
        DayOfWeek.MONDAY -> Res.string.recurrence_weekday_monday
        DayOfWeek.TUESDAY -> Res.string.recurrence_weekday_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.recurrence_weekday_wednesday
        DayOfWeek.THURSDAY -> Res.string.recurrence_weekday_thursday
        DayOfWeek.FRIDAY -> Res.string.recurrence_weekday_friday
        DayOfWeek.SATURDAY -> Res.string.recurrence_weekday_saturday
        DayOfWeek.SUNDAY -> Res.string.recurrence_weekday_sunday
    }

private val DayOfWeek.nameResource: StringResource
    get() = when (this) {
        DayOfWeek.MONDAY -> Res.string.recurrence_weekday_name_monday
        DayOfWeek.TUESDAY -> Res.string.recurrence_weekday_name_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.recurrence_weekday_name_wednesday
        DayOfWeek.THURSDAY -> Res.string.recurrence_weekday_name_thursday
        DayOfWeek.FRIDAY -> Res.string.recurrence_weekday_name_friday
        DayOfWeek.SATURDAY -> Res.string.recurrence_weekday_name_saturday
        DayOfWeek.SUNDAY -> Res.string.recurrence_weekday_name_sunday
    }

private val Month.recurrenceResource: StringResource
    get() = when (this) {
        Month.JANUARY -> Res.string.recurrence_month_january
        Month.FEBRUARY -> Res.string.recurrence_month_february
        Month.MARCH -> Res.string.recurrence_month_march
        Month.APRIL -> Res.string.recurrence_month_april
        Month.MAY -> Res.string.recurrence_month_may
        Month.JUNE -> Res.string.recurrence_month_june
        Month.JULY -> Res.string.recurrence_month_july
        Month.AUGUST -> Res.string.recurrence_month_august
        Month.SEPTEMBER -> Res.string.recurrence_month_september
        Month.OCTOBER -> Res.string.recurrence_month_october
        Month.NOVEMBER -> Res.string.recurrence_month_november
        Month.DECEMBER -> Res.string.recurrence_month_december
    }

private val Month.dateResource: StringResource
    get() = when (this) {
        Month.JANUARY -> Res.string.recurrence_date_month_january
        Month.FEBRUARY -> Res.string.recurrence_date_month_february
        Month.MARCH -> Res.string.recurrence_date_month_march
        Month.APRIL -> Res.string.recurrence_date_month_april
        Month.MAY -> Res.string.recurrence_date_month_may
        Month.JUNE -> Res.string.recurrence_date_month_june
        Month.JULY -> Res.string.recurrence_date_month_july
        Month.AUGUST -> Res.string.recurrence_date_month_august
        Month.SEPTEMBER -> Res.string.recurrence_date_month_september
        Month.OCTOBER -> Res.string.recurrence_date_month_october
        Month.NOVEMBER -> Res.string.recurrence_date_month_november
        Month.DECEMBER -> Res.string.recurrence_date_month_december
    }

private val Int.isFromEnd: Boolean get() = this < 0

/** Positions from the start first, then from the end, each by increasing distance. */
private fun List<Int>.sortedRecurrencePositions(): List<Int> =
    sortedWith(compareBy(Int::isFromEnd).thenBy(Int::absoluteValue))

private fun List<WeekDayNum>.sortedWeekDayNums(weekStart: DayOfWeek?): List<WeekDayNum> {
    val order = weekOrder(weekStart)
    return sortedWith(compareBy<WeekDayNum> { order.indexOf(it.dayOfWeek) }.thenBy { it.ordinal ?: 0 })
}

private fun List<DayOfWeek>.sortedDaysOfWeek(weekStart: DayOfWeek?): List<DayOfWeek> = sortedBy(weekOrder(weekStart)::indexOf)

/** The days of the week, starting at [weekStart], Monday by default. */
private fun weekOrder(weekStart: DayOfWeek?): List<DayOfWeek> {
    val startIndex = DayOfWeek.entries.indexOf(weekStart ?: DayOfWeek.MONDAY)
    return DayOfWeek.entries.drop(startIndex) + DayOfWeek.entries.take(startIndex)
}
