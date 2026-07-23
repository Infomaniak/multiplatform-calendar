/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.core.domain.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Daily
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Hourly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Minutely
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Monthly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Secondly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Weekly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Yearly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.WeekDayNum
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Generates, for one FREQ period, the sorted set of instance starts produced by expanding/limiting a
 * [RecurrenceRule]'s `BY*` parts against the master `DTSTART`, per the RFC 5545 §3.3.10 table.
 *
 * A "period" is the `FREQ` unit advanced by `INTERVAL * periodIndex` from `DTSTART` (a day, an
 * aligned week, a month, a year — or a single stepped instant for the sub-daily frequencies). Within
 * it every candidate date is enumerated then filtered: `BY*` parts covering a span ≥ the frequency
 * *limit* the set, those covering a span < the frequency *expand* it. Missing lower-order components
 * default from `DTSTART` (RFC's "the same as DTSTART" rule) only when no day-level `BY*` is present.
 *
 * This slice covers `BYMONTH`, `BYMONTHDAY` and `BYDAY` (incl. signed ordinals like `-1FR`).
 * `BYWEEKNO`, `BYYEARDAY` and the sub-daily `BYHOUR`/`BYMINUTE`/`BYSECOND` are layered on later.
 */
internal object RecurrenceCandidateSet {

    fun startsInPeriod(dtStart: LocalDateTime, zone: TimeZone, rule: RecurrenceRule, periodIndex: Int): List<LocalDateTime> {
        val step = rule.interval * periodIndex
        return when (rule.freq) {
            Secondly, Minutely, Hourly -> subDailyStart(dtStart, zone, rule, step)
            Daily, Weekly, Monthly, Yearly -> {
                val times = timesInDay(dtStart, rule)
                datesInPeriod(dtStart, rule, step).flatMap { date -> times.map { LocalDateTime(date, it) } }.sorted()
            }
        }
    }

    /** The times of day an instance may start at: the `BYHOUR`×`BYMINUTE`×`BYSECOND` product, each part defaulting to `DTSTART`. */
    private fun timesInDay(dtStart: LocalDateTime, rule: RecurrenceRule): List<LocalTime> {
        val hours = rule.byHour.ifEmpty { listOf(dtStart.hour) }
        val minutes = rule.byMinute.ifEmpty { listOf(dtStart.minute) }
        val seconds = rule.bySecond.ifEmpty { listOf(dtStart.second) }

        val times = ArrayList<LocalTime>(hours.size * minutes.size * seconds.size)
        for (hour in hours) {
            for (minute in minutes) {
                for (second in seconds) {
                    times += LocalTime(hour, minute, second)
                }
            }
        }
        return times.sorted()
    }

    /** Sub-daily frequencies yield a single stepped instant, kept only if it passes the date-level limits. */
    private fun subDailyStart(dtStart: LocalDateTime, zone: TimeZone, rule: RecurrenceRule, step: Int): List<LocalDateTime> {
        val steppedInstant = dtStart.toInstant(zone) + when (rule.freq) {
            Secondly -> step.seconds
            Minutely -> step.minutes
            else -> step.hours
        }
        val stepped = steppedInstant.toLocalDateTime(zone)
        return if (passesDateLimits(stepped.date, rule)) listOf(stepped) else emptyList()
    }

    private fun passesDateLimits(date: LocalDate, rule: RecurrenceRule): Boolean {
        if (rule.byMonth.isNotEmpty() && date.monthValue !in rule.byMonth) return false
        if (rule.byMonthDay.isNotEmpty() && !matchesMonthDay(date, rule.byMonthDay)) return false
        if (rule.byDay.isNotEmpty() && date.dayOfWeek !in rule.byDay.map { it.dayOfWeek }.toSet()) return false
        return true
    }

    private fun datesInPeriod(dtStart: LocalDateTime, rule: RecurrenceRule, step: Int): List<LocalDate> {
        val weekStart = rule.weekStart ?: MONDAY
        val (rangeStart, rangeEnd) = periodRange(dtStart.date, rule.freq, step, weekStart)

        val hasDayLevelExpansion =
            rule.byDay.isNotEmpty() || rule.byMonthDay.isNotEmpty() || rule.byYearDay.isNotEmpty() || rule.byWeekNumber.isNotEmpty()
        val monthFilter = resolveMonths(rule, dtStart, hasDayLevelExpansion)

        var dates = datesBetween(rangeStart, rangeEnd)
        if (monthFilter != null) dates = dates.filter { it.monthValue in monthFilter }

        dates = if (!hasDayLevelExpansion) {
            when (rule.freq) {
                Daily -> dates
                Weekly -> dates.filter { it.dayOfWeek == dtStart.date.dayOfWeek }
                else -> dates.filter { it.day == dtStart.day } // MONTHLY/YEARLY default day; short months skip
            }
        } else {
            dates
                .filter { rule.byMonthDay.isEmpty() || matchesMonthDay(it, rule.byMonthDay) }
                .filter { rule.byDay.isEmpty() || matchesByDay(date = it, rule) }
        }

        return dates.distinct().sorted()
    }

    /** The months an instance may fall in, or `null` when the frequency's period already constrains it. */
    private fun resolveMonths(rule: RecurrenceRule, dtStart: LocalDateTime, hasDayLevelExpansion: Boolean): Set<Int>? = when {
        rule.byMonth.isNotEmpty() -> rule.byMonth.toSet()
        rule.freq == Yearly && !hasDayLevelExpansion -> setOf(dtStart.monthValue)
        else -> null
    }

    private fun matchesMonthDay(date: LocalDate, byMonthDay: List<Int>): Boolean {
        val monthLength = daysInMonth(date.year, date.monthValue)
        return byMonthDay.any { day -> if (day > 0) date.day == day else date.day == monthLength + 1 + day }
    }

    private fun matchesByDay(date: LocalDate, rule: RecurrenceRule): Boolean =
        rule.byDay.any { entry -> date.matchesByDayEntry(entry, rule) }

    private fun LocalDate.matchesByDayEntry(entry: WeekDayNum, rule: RecurrenceRule): Boolean {
        if (dayOfWeek != entry.dayOfWeek) return false
        val ordinal = entry.ordinal ?: return true // Plain weekday (e.g. "TU"): every matching weekday qualifies.
        if (!rule.usesOrdinalWeekdays) return true // WEEKLY/DAILY ignore the ordinal, weekday match is enough.
        return this == ordinalWeekdayDate(ordinal, weekday = entry.dayOfWeek, months = rule.ordinalScopeMonths(date = this))
    }

    /** RFC 5545: a `BYDAY` ordinal (e.g. `-1FR`) only constrains MONTHLY and YEARLY; WEEKLY/DAILY ignore it. */
    private val RecurrenceRule.usesOrdinalWeekdays: Boolean
        get() = freq == Monthly || freq == Yearly

    /** The months a `BYDAY` ordinal is counted over: a single month (MONTHLY, or YEARLY narrowed by `BYMONTH`) or the whole year. */
    private fun RecurrenceRule.ordinalScopeMonths(date: LocalDate): List<YearMonth> =
        if (freq == Monthly || byMonth.isNotEmpty()) listOf(YearMonth(date.year, date.monthValue))
        else (1..12).map { YearMonth(date.year, it) }

    /** The concrete date of the `ordinal`-th `weekday` within [months] (positive counts from the start, negative from the end). */
    private fun ordinalWeekdayDate(ordinal: Int, weekday: DayOfWeek, months: List<YearMonth>): LocalDate? {
        val matching = months.flatMap { it.days }.filter { it.dayOfWeek == weekday }
        return if (ordinal > 0) matching.getOrNull(ordinal - 1) else matching.getOrNull(matching.size + ordinal)
    }

    private fun periodRange(anchor: LocalDate, freq: Frequency, step: Int, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> =
        when (freq) {
            Daily -> anchor.plus(step, DateTimeUnit.DAY).let { it to it }
            Weekly -> {
                val weekOrigin = startOfWeek(anchor, weekStart).plus(step * 7, DateTimeUnit.DAY)
                weekOrigin to weekOrigin.plus(6, DateTimeUnit.DAY)
            }
            Monthly -> {
                val total = anchor.year * 12 + anchor.month.ordinal + step
                val month = YearMonth(total.floorDiv(12), total.mod(12) + 1)
                month.firstDay to month.lastDay
            }
            else -> { // YEARLY (and unreachable sub-daily, guarded by the caller)
                val year = anchor.year + step
                LocalDate(year, 1, 1) to LocalDate(year, 12, 31)
            }
        }

    private fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate {
        val offset = (date.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7
        return date.minus(offset, DateTimeUnit.DAY)
    }

    private fun datesBetween(start: LocalDate, endInclusive: LocalDate): List<LocalDate> = (start..endInclusive).toList()

    private val LocalDate.monthValue: Int get() = month.ordinal + 1
    private val LocalDateTime.monthValue: Int get() = month.ordinal + 1

    private fun daysInMonth(year: Int, month: Int): Int = YearMonth(year, month).numberOfDays
}
