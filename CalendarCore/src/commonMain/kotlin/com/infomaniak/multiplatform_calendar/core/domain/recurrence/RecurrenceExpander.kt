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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.isExceededBy
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.Completed
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.StoppedByConsecutiveEmptyPeriods
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.TruncatedByOccurrenceCap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
        var periodIndex = 0

        while (true) {
            currentCoroutineContext().ensureActive()

            val candidates = candidateStarts(dtStart, masterTiming.startZone, rrule, periodIndex)

            var countedInPeriod = false
            for (occurrenceStartLocal in candidates) {
                val occurrenceStartInstant = occurrenceStartLocal.toInstant(masterTiming.startZone)

                if (isExpansionComplete(rrule, count, occurrenceStartLocal, occurrenceStartInstant, inputEnd)) return Completed

                val (occurrenceEndLocal, occurrenceEndInstant) = masterTiming.occurrenceEnd(occurrenceStartLocal, occurrenceStartInstant)
                if (occurrenceStartInstant < inputEnd && occurrenceEndInstant > inputStart) {
                    target += Occurrence(
                        key = master.recurrenceKeyAt(occurrenceStartLocal, occurrenceStartInstant),
                        start = occurrenceStartLocal,
                        end = occurrenceEndLocal,
                        startTimeZone = master.startTimeZone,
                        endTimeZone = master.endTimeZone,
                    )
                    generated++
                    if (generated >= limits.maxGeneratedOccurrences) return TruncatedByOccurrenceCap
                }

                count++
                countedInPeriod = true
            }

            consecutiveEmptyPeriods = if (countedInPeriod) 0 else consecutiveEmptyPeriods + 1
            if (consecutiveEmptyPeriods > limits.maxScannedPeriods) return StoppedByConsecutiveEmptyPeriods
            periodIndex++
        }
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
    ): Boolean = when {
        rrule.occurrenceCount != null && count >= rrule.occurrenceCount -> true
        rrule.until.isExceededBy(occurrenceStartLocal, occurrenceStartInstant) -> true
        occurrenceStartInstant >= inputEnd -> true
        else -> false
    }

    /**
     * The sorted instance starts for [periodIndex] that are not before `DTSTART`, with `DTSTART`
     * force-included in the first period so a non-conforming master is still emitted at position 1
     * (RFC 5545 §3.8.5.3).
     *
     * A `BY*` rule expands the whole period bucket, so the bucket containing `DTSTART` (only reachable
     * at `periodIndex == 0`) can yield candidates before `DTSTART`; those are dropped here. Every later
     * period is entirely after `DTSTART`, so it is returned untouched.
     */
    private fun candidateStarts(
        dtStart: LocalDateTime,
        zone: TimeZone,
        rrule: RecurrenceRule,
        periodIndex: Int,
    ): List<LocalDateTime> {
        val starts = RecurrenceCandidateSet.startsInPeriod(dtStart, zone, rrule, periodIndex)
        if (periodIndex != 0) return starts
        val fromDtStart = starts.filter { it >= dtStart }
        return if (dtStart in fromDtStart) fromDtStart else (fromDtStart + dtStart).sorted()
    }
}
