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
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * A [RecurrenceRule] normalised into the few shapes a UI can put into a sentence.
 *
 * This layer carries **no text**: sentence structure is language-dependent (English says
 * "every 2 weeks on Tuesday and Friday", French moves the interval into the structure with
 * "un mardi sur deux"), so wording stays on each platform while the semantics are decided once
 * here. Consumers `when`/`switch` over [RecurrencePattern] and [RecurrenceEnd] and pick their own
 * localised strings.
 *
 * Everything that cannot be said in one short sentence collapses to [RecurrencePattern.Custom].
 * That is deliberate: `RRULE` is open-ended and no calendar verbalises all of it, so the fallback is
 * what keeps the rest of this model finite. Crucially, an unhandled rule part must produce `Custom`
 * and never a sentence that quietly omits it — "every year on 6 August" for a `BYWEEKNO=20` rule
 * would be worse than saying nothing.
 */
public data class RecurrenceDisplay(
    val pattern: RecurrencePattern,
    val end: RecurrenceEnd,
)

/** How often the event repeats. */
public sealed interface RecurrencePattern {

    /**
     * Frequencies that need nothing but an interval: "every 3 days", "every 2 hours".
     *
     * [interval] is always `>= 1`. Note that `interval == 1` is usually a *different sentence*
     * rather than a plural form ("every day", not "every 1 day"), so call-sites should branch on it
     * instead of feeding it straight to a plural resource.
     */
    public data class Simple(val interval: Int, val unit: RecurrenceUnit) : RecurrencePattern

    /**
     * "Every 4 weeks on Monday, Tuesday, Wednesday, Thursday and Friday".
     *
     * [days] is never empty (it falls back to the day of `DTSTART` when `BYDAY` is absent), contains
     * no duplicates, and is sorted from the week start passed to [toDisplay].
     */
    public data class Weekly(val interval: Int, val days: List<DayOfWeek>) : RecurrencePattern

    /** "Every 2 months on the 15th" / "every month on the first Thursday". */
    public data class Monthly(val interval: Int, val dayRule: MonthDayRule) : RecurrencePattern

    /** "Every year on 6 August" / "every 2 years on the last Friday of November". */
    public data class Yearly(val interval: Int, val month: Month, val dayRule: MonthDayRule) : RecurrencePattern

    /** Valid rule, but not expressible in one short sentence. Show a generic "custom" label. */
    public data object Custom : RecurrencePattern
}

/** The unit of a [RecurrencePattern.Simple] interval. */
public enum class RecurrenceUnit { Second, Minute, Hour, Day }

/** Which day of the month a monthly or yearly rule lands on. */
public sealed interface MonthDayRule {

    /** "The 15th" — from `BYMONTHDAY`, or derived from `DTSTART`. [day] is in `1..31`. */
    public data class DayOfMonth(val day: Int) : MonthDayRule

    /**
     * "The last day of the month" — `BYMONTHDAY=-1`.
     *
     * Its own case rather than a negative [DayOfMonth]: month length varies, so there is no day
     * number to show, and every language has a dedicated wording for it.
     */
    public data object LastDayOfMonth : MonthDayRule

    /**
     * Weekday-based rule: "the first Thursday", "the last weekday", "every Monday".
     *
     * [days] is never empty, holds no duplicates and is sorted from the week start passed to
     * [toDisplay]. [position] is `null` when the rule selects **every** matching weekday of the
     * period rather than one of them — `FREQ=MONTHLY;BYDAY=MO` means all the Mondays of the month,
     * not the first one. That flips the sentence from "the Nth X" to "every X", so consumers need a
     * separate string rather than a conditional fragment.
     *
     * When [days] holds several entries the position applies to the **set**: `BYDAY=MO,TU,WE,TH,FR`
     * with `BYSETPOS=-1` is "the last working day", which is not "the last Monday". Whether that set
     * deserves a dedicated noun is a platform decision — the work week is Monday-Friday in the West
     * but Sunday-Thursday elsewhere, so the model stays neutral and just lists the days.
     */
    public data class Weekdays(val days: List<DayOfWeek>, val position: WeekdayPosition?) : MonthDayRule
}

/**
 * Which occurrence of a weekday within the month.
 *
 * An enum rather than a raw `Int` so consumers get an exhaustive `when`/`switch` and never have to
 * guess what a negative index means.
 */
public enum class WeekdayPosition { First, Second, Third, Fourth, Fifth, Last }

/** When the series stops. */
public sealed interface RecurrenceEnd {
    public data object Never : RecurrenceEnd
    public data class AfterOccurrences(val count: Int) : RecurrenceEnd
    public data class OnDate(val date: LocalDate) : RecurrenceEnd
}

/**
 * Normalise this rule into a [RecurrenceDisplay].
 *
 * @param timing the event this rule belongs to. An `RRULE` is meaningless without its `DTSTART`:
 *   it supplies the anchor used when `BYDAY` / `BYMONTHDAY` / `BYMONTH` are omitted.
 * @param deviceZone zone used to resolve a UTC `UNTIL` for floating events (typically
 *   `TimeZone.currentSystemDefault()`).
 * @param weekStart first day of the user's week, used **only** to order
 *   [RecurrencePattern.Weekly.days]. This is a display concern and is intentionally not the rule's
 *   own [RecurrenceRule.weekStart], which is a computation input (it decides which occurrences fall
 *   where when `INTERVAL > 1`) and would order Monday last for a `WKST=TU` rule.
 */
public fun RecurrenceRule.toDisplay(
    timing: EventTiming,
    deviceZone: TimeZone,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): RecurrenceDisplay {
    val anchor = timing.start.date
    val safeInterval = interval.coerceAtLeast(1)

    // Time-of-day filters ("at 09:00 and 17:00") and year-scoped ones ("day 100 of the year",
    // "week 20") turn the rule into a set expression that no short sentence covers, whatever the
    // frequency. Checked up front so a newly supported RRULE part can never slip into a sentence
    // that silently ignores it.
    val hasUnspeakableRules = listOf(byHour, byMinute, bySecond, byYearDay, byWeekNumber).any { it.isNotEmpty() }
    val hasNoDateRules = byDay.isEmpty() && byMonthDay.isEmpty() && byMonth.isEmpty() && byOccurrencePosition.isEmpty()

    val pattern = when {
        hasUnspeakableRules -> RecurrencePattern.Custom

        else -> when (freq) {
            // Sub-daily frequencies are never produced by the UI but can arrive through an ICS import.
            Frequency.Secondly -> simple(safeInterval, RecurrenceUnit.Second, hasNoDateRules)
            Frequency.Minutely -> simple(safeInterval, RecurrenceUnit.Minute, hasNoDateRules)
            Frequency.Hourly -> simple(safeInterval, RecurrenceUnit.Hour, hasNoDateRules)
            Frequency.Daily -> simple(safeInterval, RecurrenceUnit.Day, hasNoDateRules)

            Frequency.Weekly -> when {
                byMonthDay.isNotEmpty() || byMonth.isNotEmpty() || byOccurrencePosition.isNotEmpty() -> {
                    RecurrencePattern.Custom
                }
                // A numbered BYDAY is forbidden outside MONTHLY/YEARLY (RFC 5545 §3.3.10). The parser
                // already rejects it; kept because RecurrenceRule can also be built by hand.
                byDay.any { it.ordinal != null } -> RecurrencePattern.Custom
                else -> {
                    val days = byDay.map { it.dayOfWeek }
                        .ifEmpty { listOf(anchor.dayOfWeek) } // BYDAY absent ⇒ day of DTSTART.
                        .distinct()
                        .sortedFrom(weekStart)
                    RecurrencePattern.Weekly(safeInterval, days)
                }
            }

            Frequency.Monthly -> when {
                byMonth.isNotEmpty() -> RecurrencePattern.Custom
                else -> monthDayRule(anchor, weekStart)?.let { RecurrencePattern.Monthly(safeInterval, it) }
                    ?: RecurrencePattern.Custom
            }

            Frequency.Yearly -> {
                val month = when (byMonth.size) {
                    0 -> anchor.month
                    1 -> byMonth.single().takeIf { it in 1..12 }?.let { Month.entries[it - 1] }
                    else -> null
                }
                val dayRule = monthDayRule(anchor, weekStart)
                if (month != null && dayRule != null) {
                    RecurrencePattern.Yearly(safeInterval, month, dayRule)
                } else {
                    RecurrencePattern.Custom
                }
            }
        }
    }

    return RecurrenceDisplay(pattern, resolveEnd(timing, deviceZone))
}

private fun simple(interval: Int, unit: RecurrenceUnit, hasNoDateRules: Boolean): RecurrencePattern {
    return if (hasNoDateRules) RecurrencePattern.Simple(interval, unit) else RecurrencePattern.Custom
}

/**
 * Resolve the "which day of the month" part shared by MONTHLY and YEARLY.
 * Returns `null` when the combination cannot be said in one short sentence.
 */
private fun RecurrenceRule.monthDayRule(anchor: LocalDate, weekStart: DayOfWeek): MonthDayRule? {
    // Nothing specified ⇒ derive from DTSTART.
    if (byDay.isEmpty() && byMonthDay.isEmpty() && byOccurrencePosition.isEmpty()) {
        return MonthDayRule.DayOfMonth(anchor.day)
    }

    // "On the 15th", "on the last day of the month".
    if (byDay.isEmpty() && byOccurrencePosition.isEmpty() && byMonthDay.size == 1) {
        return when (val day = byMonthDay.single()) {
            -1 -> MonthDayRule.LastDayOfMonth
            // Other negatives ("the 2nd to last day") have no natural short wording.
            in 1..31 -> MonthDayRule.DayOfMonth(day)
            else -> null
        }
    }

    // Mixing BYDAY with BYMONTHDAY is an intersection ("Friday the 13th") with no short wording.
    if (byMonthDay.isNotEmpty() || byDay.isEmpty()) return null

    val days = byDay.map { it.dayOfWeek }.distinct().sortedFrom(weekStart)
    val entryOrdinals = byDay.mapNotNull { it.ordinal }

    // BYDAY=1TH: the position rides on the entry. Readable only with a single entry — BYDAY=1MO,3MO
    // is two positions and needs two sentences.
    if (entryOrdinals.isNotEmpty()) {
        if (byDay.size > 1 || byOccurrencePosition.isNotEmpty()) return null
        val position = entryOrdinals.single().toWeekdayPosition() ?: return null
        return MonthDayRule.Weekdays(days, position)
    }

    // No BYSETPOS ⇒ every matching weekday ("every Monday"). With one ⇒ that position within the set
    // ("the last working day" for BYDAY=MO..FR;BYSETPOS=-1).
    val position = if (byOccurrencePosition.isEmpty()) {
        null
    } else {
        byOccurrencePosition.singleOrNull()?.toWeekdayPosition() ?: return null
    }

    return MonthDayRule.Weekdays(days, position)
}

@OptIn(ExperimentalTime::class)
private fun RecurrenceRule.resolveEnd(timing: EventTiming, deviceZone: TimeZone): RecurrenceEnd {
    // RFC 5545 forbids COUNT and UNTIL together, and the parser rejects rules carrying both.
    occurrenceCount?.takeIf { it > 0 }?.let { return RecurrenceEnd.AfterOccurrences(it) }

    // Each UNTIL value type is read in its own domain: RecurrenceUntil keeps DATE, floating
    // DATE-TIME and UTC DATE-TIME apart, so only the last one needs a zone to land on a calendar day.
    return when (val bound = until) {
        null -> RecurrenceEnd.Never
        is RecurrenceUntil.DateOnly -> RecurrenceEnd.OnDate(bound.date)
        is RecurrenceUntil.Floating -> RecurrenceEnd.OnDate(bound.dateTime.date)
        is RecurrenceUntil.DateTimeUtc -> {
            RecurrenceEnd.OnDate(bound.instant.toLocalDateTime(timing.startTimeZone ?: deviceZone).date)
        }
    }
}

/** RFC 5545 allows -53..-1 and 1..53, but only "last" reads naturally among the negatives. */
private fun Int.toWeekdayPosition(): WeekdayPosition? = when (this) {
    1 -> WeekdayPosition.First
    2 -> WeekdayPosition.Second
    3 -> WeekdayPosition.Third
    4 -> WeekdayPosition.Fourth
    5 -> WeekdayPosition.Fifth
    -1 -> WeekdayPosition.Last
    else -> null
}

private fun List<DayOfWeek>.sortedFrom(weekStart: DayOfWeek): List<DayOfWeek> {
    return sortedBy { (it.isoDayNumber - weekStart.isoDayNumber + 7) % 7 }
}
