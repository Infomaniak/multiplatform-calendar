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

/** RFC 5545 §3.3.10 RRULE part names, shared by the parser and serializer so the tokens live in one place. */
internal enum class RecurrenceRuleField {
    FREQ,
    UNTIL,
    COUNT,
    INTERVAL,
    BYSECOND,
    BYMINUTE,
    BYHOUR,
    BYDAY,
    BYMONTHDAY,
    BYYEARDAY,
    BYWEEKNO,
    BYMONTH,
    BYSETPOS,
    WKST, // WeekStart: the weekday the week begins on (affects INTERVAL grouping and BYWEEKNO).
    RSCALE, // RFC 7529 non-Gregorian calendar scale; unsupported here.
    SKIP;

    internal companion object {
        internal fun fromToken(token: String): RecurrenceRuleField? = entries.firstOrNull { it.name == token }
    }
}
