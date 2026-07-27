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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.Finite
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.Infinite
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.FiniteDeferred
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateOnly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateTimeUtc
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.Floating
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/**
 * Derive the recurrence-set bounds of an event from its [timing] and parsed [rrule] (RRULE only — RDATE,
 * EXDATE and overrides come in PR 10/11). Callers pass a non-null [rrule]; the result feeds the nullable
 * [RecurrenceBoundsEntity] `@Embedded` (`null` when the event has no recurrence). Field-level column
 * semantics live on [RecurrenceBoundsEntity].
 *
 * - **Low bound**: `firstOccurrenceInstantMs = DTSTART` — an RRULE instance can never precede
 *   `DTSTART` (RFC 5545 §3.8.5.3). It is `null` for floating series (no absolute instant); the range
 *   query then falls back to the master's `dtStart` wall-clock. All-day series pad it earlier by
 *   [MAX_UTC_OFFSET_MS] (see below).
 * - **Upper bound** ([RecurrenceBoundKind]):
 *   - `UNTIL` present → [RecurrenceBoundKind.Finite]. The bound is the **conservative**
 *     `UNTIL + effective duration`: an instance starts at `≤ UNTIL`, so its end is `≤ UNTIL + duration`.
 *     This is a safe over-approximation that avoids expanding the whole (possibly dense) series at
 *     sync — a range falling between the true last end and this bound merely keeps the series, which
 *     the expander then correctly resolves to zero occurrences. Anchored (zoned / UTC / all-day)
 *     series fill the instant column; floating series fill the wall-clock column.
 *   - `COUNT` without `UNTIL` → [RecurrenceBoundKind.FiniteDeferred]: finite, but its last occurrence
 *     is deliberately not pre-computed at sync (expanding a high `COUNT` per event would be wasteful);
 *     the cut-off is applied by the expander at read time.
 *   - neither → [RecurrenceBoundKind.Infinite].
 *
 * **All-day zone padding**: all-day occurrences are anchored in the reader's *device* zone by the
 * expander ([com.infomaniak.multiplatform_calendar.core.domain.recurrence.MasterTiming]), whereas these
 * sync-time bounds are computed at UTC midnight. Each all-day occurrence's absolute instant therefore
 * shifts by the device's UTC offset, so the instant bounds are padded by [MAX_UTC_OFFSET_MS] on both
 * sides to stay a safe superset for every possible device zone; the expander drops the false positives.
 */
internal fun RecurrenceRule.toRecurrenceBoundsEntity(timing: EventTimingEntity): RecurrenceBoundsEntity {
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
    is Floating -> dateTime.toInstant(TimeZone.UTC).toEpochMilliseconds()
}

/** `UNTIL + duration` as a wall-clock, for floating series (no DST → any anchor yields the same). */
private fun RecurrenceUntil.toLocalEnd(timing: EventTimingEntity): LocalDateTime {
    val untilLocal = when (this) {
        is Floating -> dateTime
        is DateOnly -> LocalDateTime(date, timing.dtStart.time)
        is DateTimeUtc -> instant.toLocalDateTime(TimeZone.UTC)
    }
    val duration = timing.dtEndEffective.toInstant(TimeZone.UTC) - timing.dtStart.toInstant(TimeZone.UTC)
    return untilLocal.toInstant(TimeZone.UTC).plus(duration).toLocalDateTime(TimeZone.UTC)
}
