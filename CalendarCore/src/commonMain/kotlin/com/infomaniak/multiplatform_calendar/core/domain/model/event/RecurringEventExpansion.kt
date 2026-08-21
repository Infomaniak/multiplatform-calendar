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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.AllDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Floating
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Utc
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Zoned
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.recurrenceKeyAt
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionLimits
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.Completed
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.MasterTiming
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.RecurrenceExpander
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Replace every recurring master in this list by the concrete [Event] occurrences its `RRULE`
 * generates within `[rangeStart, rangeEnd[` (see [RecurrenceExpander]), leaving non-recurring events
 * untouched. Runs **before** the day split ([groupDaySlicesByDay]) so each occurrence is then sliced
 * like any other event.
 *
 * Each occurrence is a synthetic [Event] whose [Event.occurrenceId] is
 * `masterId + "#" + canonicalRecurrenceKey`
 * (stable per instance) and whose timing is the occurrence's own (wall-clock preserved across DST,
 * `end` exclusive). The master's `RRULE` is kept on the instance's timing so consumers can still tell
 * it belongs to a series — the expander is never re-run on an already-materialised occurrence.
 *
 * Applies the recurrence set semantics `(RRULE ∪ RDATE ∪ {DTSTART for RDATE-only}) − EXDATE`.
 *
 * A slot carrying a `RECURRENCE-ID` override is never emitted from the rule: the override *is* the
 * instance. It is emitted instead — at its own position, which may have been moved — unless it falls
 * outside the window or is `STATUS:CANCELLED`, the iCalendar way of deleting a single occurrence.
 * Note the two directions this covers: an instance moved out of the window disappears from the slot
 * it left, and one moved in appears even though the rule never generates anything there.
 *
 * [onExpansionTruncated] is invoked with the master's [EventId] whenever the expander stops on a safety cap
 * (outcome other than [ExpansionOutcome.Completed]) so the caller can surface it (e.g. to Sentry); the
 * partial occurrences gathered so far are still returned.
 */
internal suspend fun List<EventSeries>.expandRecurrencesInWindow(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
    onExpansionTruncated: (masterId: EventId, outcome: ExpansionOutcome) -> Unit = { _, _ -> },
): List<Event> {
    val expanded = ArrayList<Event>(size)
    val occurrences = ArrayList<Occurrence>()
    for (series in this) {
        currentCoroutineContext().ensureActive()
        val event = series.master
        occurrences.clear()
        val hasRecurringExpansion = event.timing.expandRecurrenceOccurrencesInWindow(
            masterId = event.masterEventId,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            timeZone = timeZone,
            target = occurrences,
            limits = limits,
            onExpansionTruncated = onExpansionTruncated,
        )
        if (!hasRecurringExpansion) {
            expanded += event
            continue
        }
        val overrides = series.overridesByOccurrenceKey
        for (occurrence in occurrences) {
            if (occurrence.key.canonical in overrides) continue
            expanded += event.toOccurrenceEvent(occurrence)
        }
        for (override in overrides.values) {
            if (override.status == EventStatus.CANCELLED) continue
            if (override.timing.overlaps(rangeStart, rangeEnd, timeZone)) expanded += override
        }
    }
    return expanded
}

/** Same `[rangeStart, rangeEnd[` overlap rule as [buildOccurrenceAt], for an already-positioned instance. */
private fun EventTiming.overlaps(rangeStart: Instant, rangeEnd: Instant, timeZone: TimeZone): Boolean {
    return startInstant(timeZone) < rangeEnd && endInstant(timeZone) > rangeStart
}

/**
 * Expand this timing's RRULE into [target] for `[rangeStart, rangeEnd[` in [timeZone].
 *
 * [target] is caller-owned: callers must clear it before each expansion when reusing the same buffer.
 *
 * Returns `true` when this timing is recurring (`recurrenceRule != null` or `rDates` non-empty) and occurrences have been appended
 * into [target], `false` otherwise (non-recurring timing, [target] left untouched).
 */
internal suspend fun EventTiming.expandRecurrenceOccurrencesInWindow(
    masterId: EventId,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    target: MutableList<Occurrence>,
    limits: ExpansionLimits = ExpansionLimits(),
    onExpansionTruncated: (masterId: EventId, outcome: ExpansionOutcome) -> Unit = { _, _ -> },
): Boolean {
    if (!hasRecurrenceSet()) return false

    if (rDates.isEmpty() && exDates.isEmpty()) {
        val outcome = expandRRuleDirectlyInto(
            target = target,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            timeZone = timeZone,
            limits = limits,
        )
        if (outcome != Completed) onExpansionTruncated(masterId, outcome)
        return true
    }

    val masterTiming = MasterTiming.of(this, timeZone)
    val occurrencesByKey = LinkedHashMap<String, Occurrence>()
    val outcome = expandRRuleOccurrencesInWindow(
        target = occurrencesByKey,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        timeZone = timeZone,
        limits = limits,
    )

    addMasterOccurrenceWhenRDateOnly(
        target = occurrencesByKey,
        masterTiming = masterTiming,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        timeZone = timeZone,
    )
    addRDateOccurrences(
        target = occurrencesByKey,
        masterTiming = masterTiming,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        timeZone = timeZone,
    )
    removeExDateOccurrences(target = occurrencesByKey)

    target += occurrencesByKey.values.sortedBy(Occurrence::start)
    if (outcome != Completed) onExpansionTruncated(masterId, outcome)
    return true
}

private fun EventTiming.hasRecurrenceSet(): Boolean = recurrenceRule != null || rDates.isNotEmpty()

private suspend fun EventTiming.expandRRuleDirectlyInto(
    target: MutableList<Occurrence>,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits,
): ExpansionOutcome {
    val rrule = recurrenceRule ?: return Completed
    return RecurrenceExpander.expandInto(
        target = target,
        master = this,
        rrule = rrule,
        inputStart = rangeStart,
        inputEnd = rangeEnd,
        defaultZone = timeZone,
        limits = limits,
    )
}

private suspend fun EventTiming.expandRRuleOccurrencesInWindow(
    target: MutableMap<String, Occurrence>,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits,
): ExpansionOutcome {
    val rrule = recurrenceRule ?: return Completed
    val generatedByRRule = ArrayList<Occurrence>()
    val outcome = RecurrenceExpander.expandInto(
        target = generatedByRRule,
        master = this,
        rrule = rrule,
        inputStart = rangeStart,
        inputEnd = rangeEnd,
        defaultZone = timeZone,
        limits = limits,
    )
    generatedByRRule.forEach { occurrence -> target[occurrence.key.canonical] = occurrence }
    return outcome
}

private fun EventTiming.addMasterOccurrenceWhenRDateOnly(
    target: MutableMap<String, Occurrence>,
    masterTiming: MasterTiming,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
) {
    if (recurrenceRule != null) return
    buildOccurrenceAt(
        key = recurrenceKeyAt(start, startInstant(timeZone)),
        masterTiming = masterTiming,
        defaultZone = timeZone,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
    )?.let { target[it.key.canonical] = it }
}

private fun EventTiming.addRDateOccurrences(
    target: MutableMap<String, Occurrence>,
    masterTiming: MasterTiming,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
) {
    rDates.forEach { dateValue ->
        val key = dateValue.toRecurrenceKey(this) ?: return@forEach
        buildOccurrenceAt(
            key = key,
            masterTiming = masterTiming,
            defaultZone = timeZone,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )?.let { target[key.canonical] = it }
    }
}

private fun EventTiming.removeExDateOccurrences(target: MutableMap<String, Occurrence>) {
    exDates.forEach { dateValue ->
        // Mapper-side validation keeps EXDATE value forms aligned with DTSTART. If a future change
        // breaks that invariant, `toRecurrenceKey` returns null and we keep this explicit no-op path.
        val key = dateValue.toRecurrenceKey(this) ?: return@forEach
        target.remove(key.canonical)
    }
}

private fun EventTiming.buildOccurrenceAt(
    key: RecurrenceKey,
    masterTiming: MasterTiming,
    defaultZone: TimeZone,
    rangeStart: Instant,
    rangeEnd: Instant,
): Occurrence? {
    val localStart = key.toLocalStart(this@buildOccurrenceAt, defaultZone) ?: return null
    val instantStart = when (key) {
        is Utc -> key.instant
        else -> masterTiming.resolvedStartInstant(localStart)
    }
    if (!masterTiming.existsAt(localStart, instantStart)) return null

    val (localEnd, instantEnd) = masterTiming.occurrenceEnd(localStart, instantStart)
    if (instantStart >= rangeEnd || instantEnd <= rangeStart) return null

    return Occurrence(
        key = key,
        start = localStart,
        end = localEnd,
        startTimeZone = startTimeZone,
        endTimeZone = endTimeZone,
    )
}

private fun IcalDateValue.toRecurrenceKey(master: EventTiming): RecurrenceKey? = when {
    master.isAllDay && this is IcalDateValue.AllDay -> AllDay(date)
    !master.isAllDay && this is IcalDateValue.AllDay -> {
        val local = LocalDateTime(date, master.start.time)
        when (val zone = master.startTimeZone) {
            null -> Floating(local)
            TimeZone.UTC -> Utc(local.toInstant(TimeZone.UTC))
            else -> Zoned(local, zone.id)
        }
    }
    !master.isAllDay && master.startTimeZone == null && this is IcalDateValue.Floating -> Floating(localDateTime)
    !master.isAllDay && master.startTimeZone == TimeZone.UTC && this is IcalDateValue.Zoned -> Utc(instant)
    !master.isAllDay && master.startTimeZone != null && master.startTimeZone != TimeZone.UTC && this is IcalDateValue.Zoned ->
        Zoned(instant.toLocalDateTime(master.startTimeZone), master.startTimeZone.id)
    else -> null
}

private fun RecurrenceKey.toLocalStart(master: EventTiming, defaultZone: TimeZone): LocalDateTime? = when (this) {
    is AllDay -> LocalDateTime(date, master.start.time)
    is Floating -> localDateTime
    is Zoned -> if (master.startTimeZone != null) localDateTime else null
    is Utc -> instant.toLocalDateTime(master.startTimeZone ?: defaultZone)
}

/** Materialise one [occurrence] of this recurring master into a concrete synthetic [Event]. */
private fun Event.toOccurrenceEvent(occurrence: Occurrence): Event {
    // Copying keeps all master fields (title, colors, attendees, …) while overriding identity and timing.
    return copy(
        occurrenceId = OccurrenceId("${masterEventId.url}#${occurrence.key.canonical}"),
        timing = timing.copy(
            start = occurrence.start,
            end = occurrence.end,
            startTimeZone = occurrence.startTimeZone,
            endTimeZone = occurrence.endTimeZone,
            isAllDay = occurrence.isAllDay,
        ),
    )
}
