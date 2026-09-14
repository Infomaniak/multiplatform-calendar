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

/** What a truncation leaves of a series: its rule bounded, or none when only dates carry it on. */
internal data class TruncatedSeries(val rule: RecurrenceRule?)

/**
 * This series bounded so its last instance is the one preceding [pivotStart], or `null` when nothing
 * precedes it — the resource then describes only instances the user asked to drop, and goes whole.
 *
 * The question is put to the *entire* recurrence set, `DTSTART ∪ RRULE ∪ RDATE`: a series carried by
 * `RDATE` alone counts its `DTSTART` as an instance (RFC 5545 §3.8.5.2), so reading the rule alone
 * would leave a lone event standing where the user asked for nothing. `EXDATE` is not subtracted: an
 * excluded instance still belongs to the series, and dropping the resource over one would discard
 * exclusions that can still be undone.
 */
internal suspend fun EventTiming.truncateBefore(
    pivotStart: LocalDateTime,
    defaultZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
): TruncatedSeries? {
    recurrenceRule?.let { rule ->
        ruleBoundedBefore(rule, pivotStart, defaultZone, limits)?.let { return TruncatedSeries(rule = it) }
    }

    // The rule keeps nothing, so it goes whole — what the dates carry may still stand on its own.
    val keepsADate = rDates.any { it.startsBefore(pivotStart, this) }
    // DTSTART is an instance in its own right only when no rule generates it.
    val keepsItsStart = recurrenceRule == null && start < pivotStart
    return if (keepsADate || keepsItsStart) TruncatedSeries(rule = null) else null
}

/**
 * [rule] bounded so its last instance is the one preceding [pivotStart], or `null` when it generates
 * none — the pivot is then its first instance, which is `DTSTART` itself.
 *
 * A counted rule stays counted (the pivot's rank minus one) and any other gets an `UNTIL`, so
 * "this and following" never changes the shape the series was authored with. The bound is read from
 * the instances the rule *generates*: `EXDATE` doesn't shift a rank, and `RDATE` doesn't create one.
 */
private suspend fun EventTiming.ruleBoundedBefore(
    rule: RecurrenceRule,
    pivotStart: LocalDateTime,
    defaultZone: TimeZone,
    limits: ExpansionLimits,
): RecurrenceRule? {
    val kept = LastInstanceSink()
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
    val last = kept.last ?: return null

    return when {
        rule.occurrenceCount != null -> rule.copy(occurrenceCount = kept.size)
        else -> rule.copy(until = untilAt(last.start))
    }
}

/**
 * Counts the instances handed to it and keeps only the latest, which is all a bound is read from.
 *
 * [RecurrenceExpander.expandInto] writes into a list and nothing else, and the window here opens at
 * the start of the series: a dense long-running rule would otherwise materialise up to
 * [ExpansionLimits.maxGeneratedOccurrences] occurrences just to read two values off the end.
 */
private class LastInstanceSink : AbstractMutableList<Occurrence>() {

    var last: Occurrence? = null
        private set

    override var size: Int = 0
        private set

    override fun add(index: Int, element: Occurrence) {
        require(index == size) { "The expander only ever appends" }
        last = element
        size++
    }

    override fun get(index: Int): Occurrence = writeOnly()

    override fun set(index: Int, element: Occurrence): Occurrence = writeOnly()

    override fun removeAt(index: Int): Occurrence = writeOnly()

    private fun writeOnly(): Nothing = throw UnsupportedOperationException("This sink only collects")
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
