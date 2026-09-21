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
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/** What a truncation leaves of a series: its rule bounded, or none when only dates carry it on. */
internal data class TruncatedSeries(val rule: RecurrenceRule?)

/**
 * The two sides a split leaves: what the master keeps, and the rule the new resource resumes with.
 *
 * [pivotFollowsTheRule] says whether the rule generates the pivot itself. When it does not — the pivot
 * being an `RDATE` the rule never produces — the tail cannot be re-anchored on it without moving every
 * instance that follows, since `DTSTART` is what a `RRULE` counts from (RFC 5545 §3.8.5.3). A series
 * with no rule at all has nothing to re-anchor, so it follows trivially.
 */
internal data class SeriesSplit(
    val head: TruncatedSeries?,
    val tail: RecurrenceRule?,
    val pivotFollowsTheRule: Boolean,
)

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
): TruncatedSeries? = splitAt(pivotStart, defaultZone, limits).head

/**
 * This series cut in two at [pivotStart]: the head [truncateBefore] leaves, and the rule the instances
 * from the pivot on carry on with, once re-anchored on the `DTSTART` of a resource of their own.
 *
 * RFC 5545 gives a series one `RRULE`, so "this and following" cannot be expressed in place; the tail
 * becomes a second resource. A counted rule hands the head the instances it took and keeps the rest,
 * while an `UNTIL` or endless one is carried over as it stands — the bound it names is still the one
 * the series ends on.
 */
internal suspend fun EventTiming.splitAt(
    pivotStart: LocalDateTime,
    defaultZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
): SeriesSplit {
    val kept = recurrenceRule?.let { rule -> instancesUpTo(rule, pivotStart, defaultZone, limits) }
    val boundedRule = kept?.lastStart?.let { lastStart ->
        when {
            recurrenceRule.occurrenceCount != null -> recurrenceRule.copy(occurrenceCount = kept.count)
            else -> recurrenceRule.copy(until = untilAt(localStart = lastStart))
        }
    }

    val head = when {
        boundedRule != null -> TruncatedSeries(rule = boundedRule)
        // The rule keeps nothing, so it goes whole — what the dates carry may still stand on its own.
        rDates.any { it.startsBefore(pivotStart, master = this) } -> TruncatedSeries(rule = null)
        // DTSTART is an instance in its own right only when no rule generates it.
        recurrenceRule == null && start < pivotStart -> TruncatedSeries(rule = null)
        else -> null
    }

    return SeriesSplit(
        head = head,
        tail = recurrenceRule?.resumedAfter(keptCount = kept?.count ?: 0),
        pivotFollowsTheRule = kept?.generatesPivot != false,
    )
}

/**
 * This rule as the tail resumes it, or `null` when the head consumed it whole. A counted rule loses
 * the instances the head took; anything else stands as authored, the tail's own `DTSTART` being what
 * moves it.
 */
private fun RecurrenceRule.resumedAfter(keptCount: Int): RecurrenceRule? = when (val count = occurrenceCount) {
    null -> this
    else -> (count - keptCount).takeIf { it > 0 }?.let { copy(occurrenceCount = it) }
}

/**
 * The instances [rule] generates before [pivotStart], counted with only the latest kept, and whether it
 * generates the pivot itself.
 *
 * The bound is read from the instances the rule *generates*: `EXDATE` doesn't shift a rank, and
 * `RDATE` doesn't create one. The window therefore runs one tick past the pivot — the smallest instant
 * that can hold it, the expander emitting on `start < inputEnd` — and the sink tells the two apart.
 */
private suspend fun EventTiming.instancesUpTo(
    rule: RecurrenceRule,
    pivotStart: LocalDateTime,
    defaultZone: TimeZone,
    limits: ExpansionLimits,
): SplitTally {
    var count = 0
    var lastStart: LocalDateTime? = null
    var generatesPivot = false

    val outcome = RecurrenceExpander.forEachInstance(
        master = this,
        rrule = rule,
        // The window opens before any instance can start, so a zero-duration series is kept whole too.
        inputStart = Instant.DISTANT_PAST,
        // Reaching just past the pivot is what lets the same walk tell whether the rule lands on it.
        inputEnd = MasterTiming.of(master = this, defaultZone).resolvedStartInstant(startLocal = pivotStart) +
            1.nanoseconds,
        defaultZone = defaultZone,
        limits = limits,
    ) { startLocal, _, _ ->
        when {
            startLocal < pivotStart -> {
                lastStart = startLocal
                count++
            }
            startLocal == pivotStart -> generatesPivot = true
        }
    }
    // A stopped walk yields a prefix, whose size and last instance would both bound the rule short.
    check(outcome == ExpansionOutcome.Completed) { "Cannot truncate a series whose expansion stopped on $outcome" }

    return SplitTally(count = count, lastStart = lastStart, generatesPivot = generatesPivot)
}

/** What a split reads off a rule: the instances preceding the pivot, and whether the rule makes the pivot. */
private data class SplitTally(val count: Int, val lastStart: LocalDateTime?, val generatesPivot: Boolean)

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
