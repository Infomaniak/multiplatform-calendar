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
package com.infomaniak.multiplatform_calendar.core.domain.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule

public sealed interface RecurrencePattern {

    public val interval: Int
    public val end: RecurrenceEnd

    /**
     * Examples:
     *
     * FREQ=DAILY
     * FREQ=DAILY;INTERVAL=2
     */
    public data class Daily(
        override val interval: Int = 1,
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : RecurrencePattern

    /**
     * Examples:
     *
     * FREQ=WEEKLY
     * FREQ=WEEKLY;BYDAY=MO
     * FREQ=WEEKLY;BYDAY=MO,WE,FR
     * FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH
     */
    public data class Weekly(
        override val interval: Int = 1,
        val days: List<RecurrenceWeekDay> = emptyList(),
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : RecurrencePattern

    /**
     * Repeats on explicit calendar days of the month.
     *
     * Examples:
     *
     * FREQ=MONTHLY;BYMONTHDAY=15
     * FREQ=MONTHLY;BYMONTHDAY=1,15
     * FREQ=MONTHLY;BYMONTHDAY=-1
     */
    public data class MonthlyByMonthDay(
        override val interval: Int = 1,
        val monthDays: List<Int>,
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : RecurrencePattern

    /**
     * Repeats according to ordinal weekdays.
     *
     * Examples:
     *
     * FREQ=MONTHLY;BYDAY=1MO
     * FREQ=MONTHLY;BYDAY=-1FR
     * FREQ=MONTHLY;BYDAY=MO;BYSETPOS=2
     */
    public data class MonthlyByWeekDay(
        override val interval: Int = 1,
        val days: List<RecurrenceOrdinalWeekDay>,
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : RecurrencePattern

    /**
     * Repeats yearly on one or several months, optionally constrained
     * by day-of-month or ordinal weekday.
     */
    public data class Yearly(
        override val interval: Int = 1,
        val months: List<RecurrenceMonth> = emptyList(),
        val monthDays: List<Int> = emptyList(),
        val ordinalWeekDays: List<RecurrenceOrdinalWeekDay> = emptyList(),
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : RecurrencePattern

    /**
     * A valid recurrence rule that cannot be safely represented by one
     * of the simplified UI-oriented patterns without losing semantics.
     *
     * The raw RRULE is intentionally NOT localized. It is only here so
     * clients may inspect/debug the rule or display a generic
     * "Custom recurrence" label.
     */
    public data class Custom(
        val recurrenceRule: RecurrenceRule,
        override val interval: Int = recurrenceRule.interval,
        override val end: RecurrenceEnd = recurrenceRule.toRecurrenceEnd(),
    ) : RecurrencePattern
}

private fun RecurrenceRule.toRecurrenceEnd(): RecurrenceEnd = until?.let { RecurrenceEnd.Until(it) }
    ?: occurrenceCount?.let { RecurrenceEnd.AfterOccurrences(it) } ?: RecurrenceEnd.Never
