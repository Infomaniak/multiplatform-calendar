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
package com.infomaniak.multiplatform_calendar.core.domain.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.recurrenceKeyAt
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Daily
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Hourly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Minutely
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Secondly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency.Weekly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.isExceededBy
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.Completed
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.StoppedByConsecutiveEmptyPeriods
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.TruncatedByOccurrenceCap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Expands a parsed [RecurrenceRule] against its master [EventTiming] into concrete [Occurrence]s.
 *
 * Stateless: the only mutation is appending to the caller-owned `target` buffer (mirroring the
 * `expandDaySlicesInto` convention), so no intermediate `List`/`Sequence`/`Flow` is allocated. The
 * per-period `BY*` expansion lives in [RecurrenceCandidateSet]; this object orchestrates the loop,
 * ordering, `COUNT`/`UNTIL`/window bounds and the safety caps.
 */
internal object RecurrenceExpander {

    /**
     * Append every instance of [rrule] whose *interval* overlaps `[inputStart, inputEnd[` to
     * [target].
     *
     * Semantics:
     * - The master `DTSTART` is always instance #1 (RFC 5545 §3.8.5.3), even when it does not match
     *   the `BY*` filters.
     * - Instances before `DTSTART` are never part of the set (a `BY*` rule may generate earlier dates
     *   in the first period — they are dropped).
     * - FREQ additions preserve the **wall-clock**, so a 09:00 daily event stays at 09:00 across DST.
     * - `MONTHLY` / `YEARLY` **skip** (never clamp) instances whose day does not exist (31st, Feb 29).
     * - An instance is emitted when it *overlaps* the window (`start < inputEnd && end > inputStart`),
     *   so long events straddling [inputStart] are not missed.
     * - `COUNT` counts every instance of the series (in or out of the window); the window only gates
     *   what is emitted.
     *
     * [defaultZone] anchors floating / all-day instances (RFC 5545 FORM #1 — the recipient's zone).
     */
    suspend fun expandInto(
        target: MutableList<Occurrence>,
        master: EventTiming,
        rrule: RecurrenceRule,
        inputStart: Instant,
        inputEnd: Instant,
        defaultZone: TimeZone,
        limits: ExpansionLimits = ExpansionLimits(),
    ): ExpansionOutcome {
        val masterTiming = MasterTiming.of(master, defaultZone)
        val dtStart = master.start

        var count = 0
        var generated = 0
        var consecutiveEmptyPeriods = 0
        var periodIndex = fastForwardIndex(dtStart, masterTiming, rrule, inputStart)

        while (true) {
            currentCoroutineContext().ensureActive()

            val candidates = candidateStarts(dtStart, masterTiming.startZone, rrule, periodIndex)

            var countedInPeriod = false
            for (occurrenceStartLocal in candidates) {

                // The resolved instant is monotonic (a spring-forward gap resolves forward), so a series
                // whose every wall-clock is a DST gap still terminates on COUNT/UNTIL/window instead of
                // scanning to the empty-period cap. It becomes the real start once the gap check passes.
                val occurrenceStartInstant = masterTiming.resolvedStartInstant(occurrenceStartLocal)
                if (isExpansionComplete(rrule, count, occurrenceStartLocal, occurrenceStartInstant, inputEnd)) return Completed

                // Skip candidates that are not real instances of the set (pre-DTSTART or DST gap), without counting them.
                if (isNonInstance(masterTiming, occurrenceStartLocal, occurrenceStartInstant, dtStart)) continue

                val occurrenceAdded = appendOccurrenceIfWithinWindow(
                    target = target,
                    master = master,
                    masterTiming = masterTiming,
                    occurrenceStartLocal = occurrenceStartLocal,
                    occurrenceStartInstant = occurrenceStartInstant,
                    inputStart = inputStart,
                    inputEnd = inputEnd,
                )
                if (occurrenceAdded) generated++
                if (generated >= limits.maxGeneratedOccurrences) return TruncatedByOccurrenceCap

                count++
                countedInPeriod = true
            }

            consecutiveEmptyPeriods = if (countedInPeriod) 0 else consecutiveEmptyPeriods + 1
            if (consecutiveEmptyPeriods > limits.maxScannedPeriods) return StoppedByConsecutiveEmptyPeriods
            periodIndex++
        }
    }

    /**
     * Append the occurrence starting at [occurrenceStartLocal] to [target] when it overlaps
     * `[inputStart, inputEnd[` — a long instance straddling [inputStart] still counts. Returns whether
     * an occurrence was appended.
     */
    private fun appendOccurrenceIfWithinWindow(
        target: MutableList<Occurrence>,
        master: EventTiming,
        masterTiming: MasterTiming,
        occurrenceStartLocal: LocalDateTime,
        occurrenceStartInstant: Instant,
        inputStart: Instant,
        inputEnd: Instant,
    ): Boolean {
        val (occurrenceEndLocal, occurrenceEndInstant) = masterTiming.occurrenceEnd(occurrenceStartLocal, occurrenceStartInstant)
        if (occurrenceStartInstant >= inputEnd || occurrenceEndInstant <= inputStart) return false

        target += Occurrence(
            key = master.recurrenceKeyAt(occurrenceStartLocal, occurrenceStartInstant),
            start = occurrenceStartLocal,
            end = occurrenceEndLocal,
            startTimeZone = master.startTimeZone,
            endTimeZone = master.endTimeZone,
        )
        return true
    }

    /**
     * Whether the whole series is exhausted at [occurrenceStartLocal] — the caller stops the expansion
     * with [Completed]. True once `COUNT` has been reached, `UNTIL` is passed, or the (globally
     * increasing) candidate has moved past the window.
     */
    private fun isExpansionComplete(
        rrule: RecurrenceRule,
        count: Int,
        occurrenceStartLocal: LocalDateTime,
        occurrenceStartInstant: Instant,
        inputEnd: Instant,
    ): Boolean {
        val countReached = rrule.occurrenceCount != null && count >= rrule.occurrenceCount
        val untilPassed = rrule.until.isExceededBy(occurrenceStartLocal, occurrenceStartInstant)
        // Candidates are globally increasing: once past the window nothing else can overlap.
        val pastWindow = occurrenceStartInstant >= inputEnd
        return countReached || untilPassed || pastWindow
    }

    /**
     * Whether [occurrenceStartLocal] (already resolved to [occurrenceStartInstant]) must be skipped
     * **without counting** because it is not a real instance of the recurrence set. Two such cases:
     * - a candidate before [dtStart]: a `BY*` period bucket can produce slots earlier than `DTSTART`,
     *   which are not part of the set (RFC 5545 §3.8.5.3);
     * - a nonexistent wall-clock (spring-forward DST gap), see [MasterTiming.existsAt] (RFC 5545 §3.3.10).
     */
    private fun isNonInstance(
        masterTiming: MasterTiming,
        occurrenceStartLocal: LocalDateTime,
        occurrenceStartInstant: Instant,
        dtStart: LocalDateTime,
    ): Boolean = occurrenceStartLocal < dtStart || !masterTiming.existsAt(occurrenceStartLocal, occurrenceStartInstant)

    /**
     * The sorted instance starts for [periodIndex], with `DTSTART` force-included in the first period
     * so a non-conforming master is still emitted at position 1 (RFC 5545 §3.8.5.3).
     *
     * A `BY*` rule expands the whole period bucket, so the bucket containing `DTSTART` (only reachable
     * at `periodIndex == 0`) can yield candidates before `DTSTART`; those are not dropped here but
     * skipped in the loop (see [isNonInstance]) so they are never counted.
     */
    private fun candidateStarts(
        dtStart: LocalDateTime,
        zone: TimeZone,
        rrule: RecurrenceRule,
        periodIndex: Int,
    ): List<LocalDateTime> {
        val starts = RecurrenceCandidateSet.startsInPeriod(dtStart, zone, rrule, periodIndex)
        return if (periodIndex == 0 && dtStart !in starts) (starts + dtStart).sorted() else starts
    }

    /**
     * A lower-bound [periodIndex] to start from, so a dense series beginning long before the window
     * (e.g. a years-old `FREQ=SECONDLY`) jumps straight to it instead of stepping period-by-period and
     * scanning billions of pre-window instances.
     *
     * Only applied without `COUNT` (which must tally every instance from the first) and only for the
     * fixed-length frequencies; `MONTHLY`/`YEARLY` keep few enough periods to walk directly. The result
     * under-shoots by the master span plus one whole period, so no window-overlapping instance is skipped.
     *
     * Known limitation: [periodIndex] is an `Int`, so the index is clamped to `Int.MAX_VALUE`. A
     * `FREQ=SECONDLY` series starting more than ~68 years before the window exceeds that range; the
     * fast-forward saturates and the subsequent `periodIndex++` would overflow. Such a pathological input
     * is not expanded correctly (the empty-period cap still stops it). Widening the period index to `Long`
     * throughout (here and in `RecurrenceCandidateSet.startsInPeriod`) would lift the limit.
     */
    private fun fastForwardIndex(
        dtStart: LocalDateTime,
        masterTiming: MasterTiming,
        rrule: RecurrenceRule,
        inputStart: Instant,
    ): Int {
        if (rrule.occurrenceCount != null) return 0
        val periodSpan = when (rrule.freq) {
            Secondly -> rrule.interval.seconds
            Minutely -> rrule.interval.minutes
            Hourly -> rrule.interval.hours
            Daily -> rrule.interval.days
            Weekly -> (rrule.interval * 7).days
            else -> return 0
        }

        val lead = (inputStart - masterTiming.maxOccurrenceDuration) - dtStart.toInstant(masterTiming.startZone)
        if (lead <= Duration.ZERO) return 0

        val wholePeriodsBefore = (lead / periodSpan).toLong() - 1
        return wholePeriodsBefore.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
