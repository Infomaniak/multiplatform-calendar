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
package com.infomaniak.multiplatform_calendar.core.data.mapper.timezone

import com.infomaniak.multiplatform_calendar.core.data.exception.CaldavParsingException
import kotlinx.datetime.TimeZone

/**
 * Resolve the [kotlinx.datetime.TimeZone] anchoring an iCalendar date/date-time value (RFC 5545):
 * - `VALUE=DATE` (all-day) → `null` (no time-zone applies).
 * - `Z` suffix              → `TimeZone.UTC`.
 * - `TZID` parameter         → resolved in this order:
 *     1. `TimeZone.of(tzid)` — canonical IANA identifier.
 *     2. RFC 5545 §3.2.19 "globally unique" prefixed form (e.g. Thunderbird / Mozilla
 *        `/mozilla.org/20050126_1/Europe/Paris`): strip the leading `/domain/version/` prefix
 *        and retry with the trailing path (e.g. `Europe/Paris`), which is usually a valid IANA id.
 *     3. [MS_TO_IANA_TIME_ZONES] fallback for Microsoft's non-IANA names
 *        (e.g. `Romance Standard Time` → `Europe/Paris`).
 *   If none match we throw [com.infomaniak.multiplatform_calendar.core.data.exception.CaldavParsingException], which causes the caller to skip
 *   the whole event rather than guess.
 * - otherwise (floating)     → `null` (the recipient supplies its local zone at display time).
 */
internal fun resolveTimeZone(
    isAllDay: Boolean,
    rawValue: String,
    tzid: String?,
    eventUrl: String,
    propertyName: String,
): TimeZone? = when {
    isAllDay -> null
    isICalUtcDateTime(rawValue) -> TimeZone.UTC
    tzid != null -> runCatching { TimeZone.of(tzid) }
        .recoverCatching { TimeZone.of(stripGloballyUniqueTzidPrefix(tzid)) }
        .recoverCatching { TimeZone.of(MS_TO_IANA_TIME_ZONES.getValue(tzid)) }
        .getOrElse { throw CaldavParsingException("Unknown $propertyName TZID '$tzid' for event $eventUrl", it) }
    else -> null // Floating: no time-zone anchor (RFC 5545 FORM #1).
}

/**
 * Strip the RFC 5545 §3.2.19 "globally unique" TZID prefix (`/domain/version/…`) to expose the trailing
 * component, which is usually a valid IANA identifier. Example (Thunderbird / Mozilla):
 * `/mozilla.org/20050126_1/Europe/Paris` → `Europe/Paris`. Returns the input unchanged when it does not
 * start with `/` or does not have enough segments (caller will simply fall through to the next fallback).
 */
private fun stripGloballyUniqueTzidPrefix(tzid: String): String =
    tzid.takeIf { it.startsWith('/') }
        ?.run { removePrefix("/").split('/', limit = 3).getOrNull(2) }
        ?: tzid

/**
 * Whether an iCalendar `DATE-TIME` value is anchored in UTC (RFC 5545 FORM #2: `Z` suffix).
 *
 * Returns `false` for `DATE`, floating local time (FORM #1) and local time with a `TZID` reference (FORM #3).
 */
internal fun isICalUtcDateTime(value: String?): Boolean = value != null && value.endsWith('Z')
