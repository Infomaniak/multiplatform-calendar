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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.RecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue.AllDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue.Zoned
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.Finite
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.FiniteDeferred
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.Infinite
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateOnly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateTimeUtc
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue.Floating as FloatingDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.Floating as FloatingUntil

/** Test helper overload for RRULE-only bounds. Production callers use [toRecurrenceBoundsEntity]. */
internal fun RecurrenceRule.toRecurrenceBoundsEntity(timing: EventTimingEntity): RecurrenceBoundsEntity {
    return checkNotNull(toRecurrenceBoundsEntity(timing = timing, recurrenceRule = this, rDates = emptyList()))
}

/**
 * Derive recurrence-set bounds from [timing], [recurrenceRule], and [rDates]. Returns `null` only when
 * no recurrence data is present at all.
 *
 * - **Low bound**: earliest occurrence start (`DTSTART` and optional RDATE starts), as epoch ms for
 *   anchored series and `null` for floating series.
 * - **Upper bound** ([RecurrenceBoundKind]):
 *   - `UNTIL` present → [RecurrenceBoundKind.Finite] with conservative `UNTIL + duration`.
 *   - `COUNT` without `UNTIL` → [RecurrenceBoundKind.FiniteDeferred].
 *   - neither → [RecurrenceBoundKind.Infinite].
 *   - `RDATE` can extend finite upper bounds (including after `UNTIL`).
 *
 * **All-day zone padding**: all-day starts/ends are padded by [MAX_UTC_OFFSET_MS] to stay a safe
 * superset across device zones (bounds are computed at UTC midnight; expansion anchors in reader zone).
 */
internal fun toRecurrenceBoundsEntity(
    timing: EventTimingEntity,
    recurrenceRule: RecurrenceRule?,
    rDates: List<IcalDateValue>,
): RecurrenceBoundsEntity? {
    val hasRecurrence = recurrenceRule != null || rDates.isNotEmpty()
    if (!hasRecurrence) return null

    val base = recurrenceRule?.baseRecurrenceBoundsEntity(timing)
    val durationMs = timing.anchoredDurationMs() + timing.allDayUpperBoundPaddingMs()
    val rDateBounds = rDates.computeRDateBounds(timing = timing, durationMs = durationMs)

    val firstOccurrenceInstantMs = minNotNull(
        base?.firstOccurrenceInstantMs ?: timing.lowBoundInstantMs(),
        rDateBounds.firstAnchoredStartInstantMs,
    )

    if (base == null) {
        // No RRULE means there is no "base" bound shape to merge with: recurrence comes only from
        // explicit RDATE entries, so derive a finite bound directly from those occurrences.
        return rdateOnlyBoundsEntity(
            timing = timing,
            durationMs = durationMs,
            firstOccurrenceInstantMs = firstOccurrenceInstantMs,
            anchoredEnds = rDateBounds.anchoredEnds,
            localEnds = rDateBounds.localEnds,
        )
    }

    return when (base.recurrenceBoundKind) {
        Finite -> mergeFiniteBoundsWithRDates(
            timing = timing,
            base = base,
            firstOccurrenceInstantMs = firstOccurrenceInstantMs,
            anchoredEnds = rDateBounds.anchoredEnds,
            localEnds = rDateBounds.localEnds,
        )
        Infinite, FiniteDeferred, null -> base.copy(firstOccurrenceInstantMs = firstOccurrenceInstantMs)
    }
}

private data class RDateBoundsComputation(
    val firstAnchoredStartInstantMs: Long?,
    val anchoredEnds: List<Long>,
    val localEnds: List<LocalDateTime>,
)

private fun List<IcalDateValue>.computeRDateBounds(
    timing: EventTimingEntity,
    durationMs: Long,
): RDateBoundsComputation {
    val localDuration = timing.localDuration()
    var firstAnchoredStartInstantMs: Long? = null
    val anchoredEnds = mutableListOf<Long>()
    val localEnds = mutableListOf<LocalDateTime>()

    this@computeRDateBounds.forEach { dateValue ->
        dateValue.startInstantMs(timing)?.let { startMs ->
            firstAnchoredStartInstantMs = minNotNull(firstAnchoredStartInstantMs, startMs)
            anchoredEnds += startMs + durationMs
        }
        dateValue.startLocalDateTime(timing)?.let { localStart ->
            localEnds += localStart.toInstant(TimeZone.UTC).plus(localDuration).toLocalDateTime(TimeZone.UTC)
        }
    }

    return RDateBoundsComputation(
        firstAnchoredStartInstantMs = firstAnchoredStartInstantMs,
        anchoredEnds = anchoredEnds,
        localEnds = localEnds,
    )
}

private fun mergeFiniteBoundsWithRDates(
    timing: EventTimingEntity,
    base: RecurrenceBoundsEntity,
    firstOccurrenceInstantMs: Long?,
    anchoredEnds: List<Long>,
    localEnds: List<LocalDateTime>,
): RecurrenceBoundsEntity {
    return if (timing.dtStartInstantMs != null) {
        base.copy(
            firstOccurrenceInstantMs = firstOccurrenceInstantMs,
            lastPossibleOccurrenceEndInstantMs = maxNotNull(
                base.lastPossibleOccurrenceEndInstantMs,
                anchoredEnds.maxOrNull(),
            ),
        )
    } else {
        base.copy(
            firstOccurrenceInstantMs = null,
            lastOccurrenceEndLocalDateTime = maxOf(
                base.lastOccurrenceEndLocalDateTime ?: timing.dtEndEffective,
                localEnds.maxOrNull() ?: timing.dtEndEffective,
            ),
        )
    }
}

private fun rdateOnlyBoundsEntity(
    timing: EventTimingEntity,
    durationMs: Long,
    firstOccurrenceInstantMs: Long?,
    anchoredEnds: List<Long>,
    localEnds: List<LocalDateTime>,
): RecurrenceBoundsEntity {
    return when {
        timing.dtStartInstantMs != null -> {
            val dtStartMs = timing.lowBoundInstantMs()
            val lastBound = maxNotNull(dtStartMs?.let { it + durationMs }, anchoredEnds.maxOrNull())
            RecurrenceBoundsEntity(
                firstOccurrenceInstantMs = firstOccurrenceInstantMs,
                lastPossibleOccurrenceEndInstantMs = lastBound,
                recurrenceBoundKind = Finite,
            )
        }
        else -> {
            val lastLocalEnd = maxOf(timing.dtEndEffective, localEnds.maxOrNull() ?: timing.dtEndEffective)
            RecurrenceBoundsEntity(
                firstOccurrenceInstantMs = null,
                lastOccurrenceEndLocalDateTime = lastLocalEnd,
                recurrenceBoundKind = Finite,
            )
        }
    }
}

private fun RecurrenceRule.baseRecurrenceBoundsEntity(timing: EventTimingEntity): RecurrenceBoundsEntity {
    val firstOccurrenceInstantMs = timing.lowBoundInstantMs()

    return when {
        until == null -> RecurrenceBoundsEntity(
            firstOccurrenceInstantMs = firstOccurrenceInstantMs,
            recurrenceBoundKind = if (occurrenceCount != null) FiniteDeferred else Infinite,
        )
        // Anchored series (zoned / UTC / all-day): the upper bound lives in absolute epoch ms.
        timing.dtStartInstantMs != null -> RecurrenceBoundsEntity(
            firstOccurrenceInstantMs = firstOccurrenceInstantMs,
            lastPossibleOccurrenceEndInstantMs = until.toInstantMs(timing) + timing.anchoredDurationMs() + timing.allDayUpperBoundPaddingMs(),
            recurrenceBoundKind = Finite,
        )
        // Floating DATE-TIME series: no absolute instant — the upper bound is a wall-clock.
        else -> RecurrenceBoundsEntity(
            firstOccurrenceInstantMs = firstOccurrenceInstantMs,
            lastOccurrenceEndLocalDateTime = until.toLocalEnd(timing),
            recurrenceBoundKind = Finite,
        )
    }
}

private fun IcalDateValue.startInstantMs(timing: EventTimingEntity): Long? = when (this) {
    is Zoned -> instant.toEpochMilliseconds()
    is AllDay -> LocalDateTime(date, timing.dtStart.time).toInstant(TimeZone.UTC).toEpochMilliseconds()
    is FloatingDateValue -> null
}

private fun IcalDateValue.startLocalDateTime(timing: EventTimingEntity): LocalDateTime? = when (this) {
    is FloatingDateValue -> localDateTime
    is AllDay -> LocalDateTime(date, timing.dtStart.time)
    is Zoned -> null
}

private fun EventTimingEntity.localDuration(): Duration =
    dtEndEffective.toInstant(TimeZone.UTC) - dtStart.toInstant(TimeZone.UTC)

private fun minNotNull(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> minOf(a, b)
}

private fun maxNotNull(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}

/** Effective duration of one instance in epoch ms, from the anchored start/end instants. */
private fun EventTimingEntity.anchoredDurationMs(): Long {
    val startMs = dtStartInstantMs ?: return 0L
    return (dtEndInstantMs ?: startMs) - startMs
}

/**
 * Widest IANA UTC offset magnitude (−12:00 … +14:00), used to pad the all-day instant bounds so they
 * remain a safe superset regardless of the reader's device zone (see the type-level "All-day zone
 * padding" note). 14 h covers both directions with margin; the surplus only keeps extra masters that
 * the expander then discards.
 */
private val MAX_UTC_OFFSET_MS = 14.hours.inWholeMilliseconds

/** Low-bound instant (`DTSTART`), padded [MAX_UTC_OFFSET_MS] earlier for all-day series; `null` for floating. */
private fun EventTimingEntity.lowBoundInstantMs(): Long? {
    val startMs = dtStartInstantMs ?: return null
    return if (isAllDay) startMs - MAX_UTC_OFFSET_MS else startMs
}

/** Extra upper-bound padding for all-day series (their device-zone end can exceed UTC); `0` otherwise. */
private fun EventTimingEntity.allDayUpperBoundPaddingMs(): Long = if (isAllDay) MAX_UTC_OFFSET_MS else 0L

/** `UNTIL` as an absolute epoch-ms instant, anchored like the (non-floating) event it bounds. */
private fun RecurrenceUntil.toInstantMs(timing: EventTimingEntity): Long = when (this) {
    is DateTimeUtc -> instant.toEpochMilliseconds()
    // All-day `UNTIL` (matching an all-day DTSTART): the day at UTC midnight, like the stored start.
    is DateOnly -> LocalDateTime(date, timing.dtStart.time).toInstant(TimeZone.UTC).toEpochMilliseconds()
    // Defensive: an anchored event never carries a floating UNTIL (form checked at sync, RFC §3.3.10).
    is FloatingUntil -> dateTime.toInstant(TimeZone.UTC).toEpochMilliseconds()
}

/** `UNTIL + duration` as a wall-clock, for floating series (no DST → any anchor yields the same). */
private fun RecurrenceUntil.toLocalEnd(timing: EventTimingEntity): LocalDateTime {
    val untilLocal = when (this) {
        is FloatingUntil -> dateTime
        is DateOnly -> LocalDateTime(date, timing.dtStart.time)
        is DateTimeUtc -> instant.toLocalDateTime(TimeZone.UTC)
    }
    val duration = timing.dtEndEffective.toInstant(TimeZone.UTC) - timing.dtStart.toInstant(TimeZone.UTC)
    return untilLocal.toInstant(TimeZone.UTC).plus(duration).toLocalDateTime(TimeZone.UTC)
}
