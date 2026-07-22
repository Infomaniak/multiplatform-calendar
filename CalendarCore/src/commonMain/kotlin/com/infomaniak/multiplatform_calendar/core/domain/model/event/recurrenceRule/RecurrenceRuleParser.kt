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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.CountAndUntilTogether
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.InvalidByDayOrdinalForFrequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.InvalidByWeekNoFrequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.InvalidByYearDayFrequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.LeapSecondUnsupported
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.MalformedGrammar
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.MissingFrequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleFailureReason.UnsupportedRscale
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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.RSCALE
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.SKIP
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.UNTIL
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleField.WKST
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleParseResult.Failed
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleParseResult.Supported
import com.infomaniak.multiplatform_calendar.core.extensions.parseICalDateTime
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.cancellable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Pure and total RFC 5545 §3.3.10 RRULE parser: returns [RecurrenceRuleParseResult.Supported] or
 * [RecurrenceRuleParseResult.Failed], and never throws. Failing (rather than mutating) keeps callers
 * from ever expanding a rule into wrong occurrences; Sentry logging and dropping happen at the sync
 * boundary, not here.
 */
internal object RecurrenceRuleParser {

    private class RuleRejectedException(val reason: RecurrenceRuleFailureReason) : Exception()

    internal fun parse(rawRule: String): RecurrenceRuleParseResult {
        return runCatching { parseOrThrow(rawRule) }
            .cancellable()
            .fold(
                onSuccess = { Supported(it) },
                onFailure = { throwable ->
                    val reason = (throwable as? RuleRejectedException)?.reason ?: MalformedGrammar
                    Failed(reason)
                },
            )
    }

    private fun parseOrThrow(rawRule: String): RecurrenceRule {
        // Keep only the first RRULE line (legacy RFC 2445 allowed several; sabredav never emits them).
        val value = rawRule.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.removePrefix("RRULE:")?.removePrefix("rrule:")
            ?: failure(MalformedGrammar)

        val parts = tokenize(value)

        val frequency = parts[FREQ]?.let { Frequency.parse(it) ?: failure(MalformedGrammar) } ?: failure(MissingFrequency)
        val count = parts[COUNT]?.toPositiveIntOrFailure()
        val until = parts[UNTIL]?.let(::parseUntil)
        if (count != null && until != null) failure(CountAndUntilTogether)

        val byDay = parts[BYDAY]?.let(::parseByDay).orEmpty()
        val byYearDay = parts[BYYEARDAY].toSignedList(maxAbsolute = 366)
        val byWeekNumber = parts[BYWEEKNO].toSignedList(maxAbsolute = 53)
        validateByRules(frequency, byDay, byYearDay, byWeekNumber)

        return RecurrenceRule(
            freq = frequency,
            interval = parts[INTERVAL]?.toPositiveIntOrFailure() ?: 1,
            occurrenceCount = count,
            until = until,
            byDay = byDay,
            byMonthDay = parts[BYMONTHDAY].toSignedList(maxAbsolute = 31),
            byMonth = parts[BYMONTH].toUnsignedList(range = 1..12),
            byOccurrencePosition = parts[BYSETPOS].toSignedList(maxAbsolute = 366),
            weekStart = parts[WKST]?.let(::parseWeekStart),
            byHour = parts[BYHOUR].toUnsignedList(range = 0..23),
            byMinute = parts[BYMINUTE].toUnsignedList(range = 0..59),
            bySecond = parts[BYSECOND]?.let(::parseBySecond).orEmpty(),
            byYearDay = byYearDay,
            byWeekNumber = byWeekNumber,
        )
    }

    /** Splits the value into its `NAME=value` parts, rejecting malformed grammar, unknown tokens and unsupported RSCALE/SKIP. */
    private fun tokenize(value: String): Map<RecurrenceRuleField, String> {
        val parts = mutableMapOf<RecurrenceRuleField, String>()
        for (part in value.split(';')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            if (separator <= 0) failure(MalformedGrammar)
            val name = part.substring(0, separator).uppercase()
            val rawValue = part.substring(separator + 1)
            if (rawValue.isEmpty()) failure(MalformedGrammar)
            when (val field = RecurrenceRuleField.fromToken(name)) {
                null -> if (!name.startsWith("X-")) failure(MalformedGrammar) // X-* extensions are ignored, other unknowns rejected.
                RSCALE, SKIP -> failure(UnsupportedRscale)
                else -> parts[field] = rawValue
            }
        }
        return parts
    }

    private fun parseByDay(raw: String): List<WeekDayNum> =
        raw.split(',').map { WeekDayNum.parse(it) ?: failure(MalformedGrammar) }

    private fun parseWeekStart(raw: String): kotlinx.datetime.DayOfWeek =
        (WeekDayNum.parse(raw)?.takeIf { it.ordinal == null } ?: failure(MalformedGrammar)).dayOfWeek

    private fun String.toPositiveIntOrFailure(): Int = toIntOrNull()?.takeIf { it >= 1 } ?: failure(MalformedGrammar)

    private fun validateByRules(
        freq: Frequency,
        byDay: List<WeekDayNum>,
        byYearDay: List<Int>,
        byWeekNumber: List<Int>,
    ) {
        // Ordinal BYDAY (e.g. 2MO) is only meaningful for MONTHLY, or YEARLY without BYWEEKNO.
        if (byDay.any { it.ordinal != null }) {
            val ordinalAllowed = freq == Frequency.Monthly || (freq == Frequency.Yearly && byWeekNumber.isEmpty())
            if (!ordinalAllowed) failure(InvalidByDayOrdinalForFrequency)
        }
        if (byYearDay.isNotEmpty() && (freq == Frequency.Weekly || freq == Frequency.Monthly)) {
            failure(InvalidByYearDayFrequency)
        }
        if (byWeekNumber.isNotEmpty() && freq != Frequency.Yearly) failure(InvalidByWeekNoFrequency)
    }

    private fun parseBySecond(raw: String): List<Int> {
        val seconds = raw.toUnsignedList(range = 0..60)
        if (seconds.contains(60)) failure(LeapSecondUnsupported) // kotlinx-datetime cannot represent a leap second.
        return seconds
    }

    private fun String?.toUnsignedList(range: IntRange): List<Int> = this?.split(',')?.map { token ->
        token.toIntOrNull()?.takeIf { it in range } ?: failure(MalformedGrammar)
    }.orEmpty()

    private fun String?.toSignedList(maxAbsolute: Int): List<Int> = this?.split(',')?.map { token ->
        val parsed = token.toIntOrNull() ?: failure(MalformedGrammar)
        val magnitude = if (parsed < 0) -parsed else parsed
        if (magnitude in 1..maxAbsolute) parsed else failure(MalformedGrammar)
    }.orEmpty()

    @OptIn(ExperimentalTime::class)
    private fun parseUntil(raw: String): Instant {
        val localDateTime = parseICalDateTime(raw) ?: failure(MalformedGrammar)
        return localDateTime.toInstant(TimeZone.UTC)
    }

    private fun failure(reason: RecurrenceRuleFailureReason): Nothing = throw RuleRejectedException(reason)
}
