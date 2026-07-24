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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateOnly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateTimeUtc
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.Floating
import com.infomaniak.multiplatform_calendar.core.extensions.toICalDate
import com.infomaniak.multiplatform_calendar.core.extensions.toICalLocalDateTime
import com.infomaniak.multiplatform_calendar.core.extensions.toICalUtcDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * RFC 5545 §3.3.10 UNTIL bound, keeping its value type so it round-trips byte-fidèle and can be
 * checked against DTSTART's type at the sync boundary (§7.9). A DATE UNTIL (common for all-day
 * recurrences) must not be widened to a UTC DATE-TIME, and a floating DATE-TIME has no absolute
 * instant.
 */
@Serializable
public sealed interface RecurrenceUntil {
    @Serializable
    public data class DateOnly(val date: LocalDate) : RecurrenceUntil

    @OptIn(ExperimentalTime::class)
    @Serializable
    public data class DateTimeUtc(val instant: Instant) : RecurrenceUntil

    @Serializable
    public data class Floating(val dateTime: LocalDateTime) : RecurrenceUntil
}

@OptIn(ExperimentalTime::class)
internal fun RecurrenceUntil.toICalString(): String = when (this) {
    is DateOnly -> date.toICalDate()
    is DateTimeUtc -> instant.toICalUtcDateTime()
    is Floating -> dateTime.toICalLocalDateTime()
}

/**
 * Whether an occurrence starting at [localStart] (anchored at [instantStart]) falls strictly after
 * this inclusive `UNTIL` bound. A `null` receiver (no bound) never excludes. Each value type is
 * compared in its own domain so a floating or DATE `UNTIL` is not widened to an absolute instant.
 */
@OptIn(ExperimentalTime::class)
internal fun RecurrenceUntil?.isExceededBy(localStart: LocalDateTime, instantStart: Instant): Boolean = when (this) {
    null -> false
    is DateOnly -> localStart.date > date
    is Floating -> localStart > dateTime
    is DateTimeUtc -> instantStart > instant
}
