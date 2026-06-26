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
package com.infomaniak.multiplatform_calendar.core.data.remote.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Parse an iCalendar `DURATION` value (RFC 5545 `dur-value`) into a [Duration].
 *
 * Grammar: `["+" / "-"] "P" (dur-date / dur-time / dur-week)`, e.g. `P2W`, `P1DT2H`, `PT15M`, `-PT5M`.
 *
 * Note: iCal weeks (`nW`) are not part of ISO-8601 durations, so we cannot rely on
 * [Duration.parseIsoString]; this dedicated parser handles the full iCal grammar.
 *
 * Fault-tolerant: returns `null` if the value is missing or unparsable
 * (a malformed event must not fail the whole synchronization).
 */
internal fun parseICalDuration(value: String?): Duration? {
    if (value == null) return null
    val match = ICAL_DURATION.matchEntire(value.trim()) ?: return null
    val (sign, weeks, days, hours, minutes, seconds) = match.destructured
    val magnitude = weeks.weekToDays() + days.days() + hours.hours() + minutes.minutes() + seconds.seconds()
    return if (sign == "-") -magnitude else magnitude
}

private fun String.weekToDays() = (toIntOrZero() * 7).days
private fun String.days() = toIntOrZero().days
private fun String.hours() = toIntOrZero().hours
private fun String.minutes() = toIntOrZero().minutes
private fun String.seconds() = toIntOrZero().seconds
private fun String.toIntOrZero(): Int = if (isEmpty()) 0 else toInt()

private val ICAL_DURATION =
    Regex("""([+-])?P(?:(\d+)W|(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?)""")

