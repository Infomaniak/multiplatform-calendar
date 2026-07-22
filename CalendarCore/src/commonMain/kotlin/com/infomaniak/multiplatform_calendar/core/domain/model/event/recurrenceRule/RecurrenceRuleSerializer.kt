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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYDAY
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYHOUR
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYMINUTE
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYMONTH
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYMONTHDAY
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYSECOND
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYSETPOS
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYWEEKNO
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.BYYEARDAY
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.COUNT
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.FREQ
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.INTERVAL
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.UNTIL
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.WKST
import com.infomaniak.multiplatform_calendar.core.extensions.toICalUtcDateTime
import kotlin.time.ExperimentalTime

/** Serializes a [RecurrenceRule] back to a canonical RFC 5545 RRULE value (no `RRULE:` prefix). */
internal object RecurrenceRuleSerializer {

    @OptIn(ExperimentalTime::class)
    internal fun serialize(rule: RecurrenceRule): String = buildList {
        add("${FREQ}=${rule.freq.toICalString()}")
        when {
            rule.until != null -> add("${UNTIL}=${rule.until.toICalUtcDateTime()}")
            rule.occurrenceCount != null -> add("${COUNT}=${rule.occurrenceCount}")
        }
        if (rule.interval != 1) add("${INTERVAL}=${rule.interval}")
        addByRule(BYSECOND, rule.bySecond)
        addByRule(BYMINUTE, rule.byMinute)
        addByRule(BYHOUR, rule.byHour)
        if (rule.byDay.isNotEmpty()) add("${BYDAY}=${rule.byDay.joinToString(",") { it.toICalString() }}")
        addByRule(BYMONTHDAY, rule.byMonthDay)
        addByRule(BYYEARDAY, rule.byYearDay)
        addByRule(BYWEEKNO, rule.byWeekNumber)
        addByRule(BYMONTH, rule.byMonth)
        addByRule(BYSETPOS, rule.byOccurrencePosition)
        rule.weekStart?.let { add("${WKST}=${WeekDayNum(dayOfWeek = it).toICalString()}") }
    }.joinToString(";")

    private fun MutableList<String>.addByRule(field: RecurrenceRuleField, values: List<Int>) {
        if (values.isNotEmpty()) add("$field=${values.joinToString(",")}")
    }
}
