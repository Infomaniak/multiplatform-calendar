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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionLimits
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.MasterTiming
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.RecurrenceExpander
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * This master's `RRULE` bounded so its last instance is the one preceding [pivotStart], or `null`
 * when the rule keeps nothing — the pivot is then its first instance, which is `DTSTART` itself.
 *
 * A counted rule stays counted (the pivot's rank minus one) and any other gets an `UNTIL`, so
 * "this and following" never changes the shape the series was authored with. The bound is read from
 * the instances the rule *generates*: `EXDATE` doesn't shift a rank, and `RDATE` doesn't create one.
 */
internal suspend fun EventTiming.truncateRuleBefore(
    pivotStart: LocalDateTime,
    defaultZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
): RecurrenceRule? {
    val rule = recurrenceRule ?: return null

    val kept = ArrayList<Occurrence>()
    val outcome = RecurrenceExpander.expandInto(
        target = kept,
        master = this,
        rrule = rule,
        // The window opens before any instance can start, so a zero-duration series is kept whole too.
        inputStart = Instant.DISTANT_PAST,
        inputEnd = MasterTiming.of(this, defaultZone).resolvedStartInstant(pivotStart),
        defaultZone = defaultZone,
        limits = limits,
    )
    // A capped expansion yields a prefix, whose size and last instance would both bound the rule short.
    check(outcome == ExpansionOutcome.Completed) { "Cannot truncate a series whose expansion stopped on $outcome" }
    val last = kept.lastOrNull() ?: return null

    return when {
        rule.occurrenceCount != null -> rule.copy(occurrenceCount = kept.size)
        else -> rule.copy(until = untilAt(last.start))
    }
}

/**
 * The inclusive `UNTIL` bound landing on the instance starting at [localStart], typed after `DTSTART`
 * as RFC 5545 §3.3.10 requires: `DATE` for an all-day master, UTC `DATE-TIME` for an anchored one,
 * floating otherwise.
 */
private fun EventTiming.untilAt(localStart: LocalDateTime): RecurrenceUntil = when {
    isAllDay -> RecurrenceUntil.DateOnly(localStart.date)
    startTimeZone != null -> RecurrenceUntil.DateTimeUtc(localStart.toInstant(startTimeZone))
    else -> RecurrenceUntil.Floating(localStart)
}

/**
 * Whether the occurrence of [master] this value designates starts before [pivotStart], hence survives
 * a truncation there. A value designating no occurrence is kept: it excludes and adds nothing anyway.
 */
internal fun IcalDateValue.startsBefore(pivotStart: LocalDateTime, master: EventTiming): Boolean =
    toRecurrenceKey(master)?.startsBefore(pivotStart, master) != false

/** See [IcalDateValue.startsBefore]. */
internal fun RecurrenceKey.startsBefore(pivotStart: LocalDateTime, master: EventTiming): Boolean {
    val localStart = toLocalStart(master, defaultZone = TimeZone.UTC) ?: return true
    return localStart < pivotStart
}
