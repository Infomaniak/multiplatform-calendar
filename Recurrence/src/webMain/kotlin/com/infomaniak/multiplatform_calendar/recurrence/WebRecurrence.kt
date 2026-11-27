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
package com.infomaniak.multiplatform_calendar.recurrence

import com.infomaniak.multiplatform_calendar.recurrence.domain.model.event.recurrenceRule.RecurrenceRuleParseResult.Failed
import com.infomaniak.multiplatform_calendar.recurrence.domain.model.event.recurrenceRule.RecurrenceRuleParseResult.Supported
import com.infomaniak.multiplatform_calendar.recurrence.domain.model.event.recurrenceRule.RecurrenceRuleParser
import com.infomaniak.multiplatform_calendar.recurrence.domain.model.event.recurrenceRule.toLocalizedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val webCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Parses and localizes an RFC 5545 recurrence rule for a web client.
 *
 * [start] must be the recurrence master's local DTSTART in ISO-8601 format. It is needed to
 * resolve selectors omitted from the rule. [timeZoneId] is the master's IANA time-zone ID and is
 * used to present UTC UNTIL values in the master's local date.
 *
 * [onFailure] receives an error message for an invalid or unsupported rule, DTSTART, or time-zone ID.
 * JavaScript clients can wrap this callback API in a `Promise` when they want to use `await`.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
public fun formatRRule(
    rawRRule: String,
    start: String,
    timeZoneId: String?,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit {
    webCoroutineScope.launch {
        val result = try {
            formatRRuleText(rawRRule, start, timeZoneId)
        } catch (cancellation: CancellationException) {
            onFailure(cancellation.message ?: "Recurrence formatting was cancelled.")
            throw cancellation
        } catch (exception: Exception) {
            onFailure(exception.message ?: "Failed to format the recurrence rule.")
            return@launch
        }
        onSuccess(result)
    }
}

internal suspend fun formatRRuleText(
    rawRRule: String,
    start: String,
    timeZoneId: String?,
): String {
    val rule = when (val result = RecurrenceRuleParser.parse(rawRRule)) {
        is Supported -> result.rule
        is Failed -> throw IllegalArgumentException("Unsupported RRULE: ${result.reason}")
    }
    return rule.toLocalizedString(
        start = LocalDateTime.parse(start),
        timeZone = timeZoneId?.let(TimeZone::of),
    )
}
