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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * One iCalendar date-time value.
 *
 * A precise value has already been collapsed to one absolute [Instant] by the KMP layer. This is
 * particularly important for a local time inside a DST overlap, which could otherwise map to two
 * instants. [Precised.timeZone] preserves the value's original display/serialization zone.
 *
 * A floating value has no absolute instant by RFC 5545 FORM #1 and is interpreted in the
 * recipient's current zone. All-day values also use [Floating], with [EventTimeRange.isAllDay]
 * carrying their date-only semantics.
 */
public sealed class EventTiming {

    public data class Precised(
        val instant: Instant,
        val timeZone: TimeZone,
    ) : EventTiming()

    public data class Floating(
        val localDateTime: LocalDateTime,
    ) : EventTiming()

    internal fun localDateTime(): LocalDateTime = when (this) {
        is Precised -> instant.toLocalDateTime(timeZone)
        is Floating -> localDateTime
    }

    internal fun instant(defaultZone: TimeZone): Instant = when (this) {
        is Precised -> instant
        is Floating -> localDateTime.toInstant(defaultZone)
    }

    internal fun inTimeZone(targetZone: TimeZone): LocalDateTime = when (this) {
        is Precised -> if (timeZone == targetZone) localDateTime() else instant.toLocalDateTime(targetZone)
        is Floating -> localDateTime
    }
}
