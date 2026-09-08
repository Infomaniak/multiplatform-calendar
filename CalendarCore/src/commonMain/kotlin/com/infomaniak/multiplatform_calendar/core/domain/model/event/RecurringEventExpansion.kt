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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.isExceededBy
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
 * Each occurrence is a synthetic [Event] whose [Event.occurrenceId] is an [OccurrenceId.Recurrence]
 * (stable per instance) and whose timing is the occurrence's own (wall-clock preserved across DST,
 * `end` exclusive). The master's `RRULE` is kept on the instance's timing so consumers can still tell
 * it belongs to a series — the expander is never re-run on an already-materialised occurrence.
 *
 * Applies the recurrence set semantics `(RRULE ∪ RDATE ∪ {DTSTART for RDATE-only}) − EXDATE`.
 *
 * A slot carrying a `RECURRENCE-ID` override is never emitted from the rule: the override *is* the
 * instance, and is emitted in its place. See [addRuleOccurrences] and [addOverriddenInstances].
 *
 * [onExpansionTruncated] is invoked with the master's [EventId] whenever the expander stops on a safety cap
 * (outcome other than [ExpansionOutcome.Completed]) so the caller can surface it (e.g. to Sentry); the
 * partial occurrences gathered so far are still returned.
 *
 * [onOrphanOverrideDropped] reports each override [addOverriddenInstances] rejected, so corrupt server
 * data surfaces instead of being silently hidden.
 */
internal suspend fun List<EventWithOverrides>.expandRecurrencesInWindow(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
    onExpansionTruncated: (masterId: EventId, outcome: ExpansionOutcome) -> Unit = { _, _ -> },
    onOrphanOverrideDropped: (masterId: EventId, slot: RecurrenceKey) -> Unit = { _, _ -> },
): List<Event> {
    val expanded = ArrayList<Event>(size)
    val occurrences = ArrayList<Occurrence>()
    for ((event, overrides) in this) {
        currentCoroutineContext().ensureActive()
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
        expanded.addRuleOccurrences(event, occurrences, overrides)
        expanded.addOverriddenInstances(event, overrides, rangeStart, rangeEnd, timeZone, onOrphanOverrideDropped)
    }
    return expanded
}

/**
 * Add the occurrences [master]'s rule generated, skipping every slot one of [overrides] claims: the
 * override *is* that instance, and [addOverriddenInstances] adds it in its place.
 */
private suspend fun MutableList<Event>.addRuleOccurrences(
    master: Event,
    occurrences: List<Occurrence>,
    overrides: Map<RecurrenceKey, Event>,
) {
    for (occurrence in occurrences) {
        currentCoroutineContext().ensureActive()
        if (occurrence.key in overrides) continue
        this += master.toOccurrenceEvent(occurrence)
    }
}

/**
 * Add each of [overrides] at its own position rather than at the slot it replaces. Since that position
 * may have been moved, an override can fall outside `[rangeStart, rangeEnd[` — or inside it while the
 * rule generates nothing there — hence the overlap test rather than a plain add.
 *
 * A `STATUS:CANCELLED` override is dropped instead: [addRuleOccurrences] already left its slot empty,
 * so dropping it here is what leaves that single occurrence deleted, the iCalendar way of removing one.
 *
 * An override whose slot is no longer part of the series is dropped too, see [isBeyondSeriesEnd].
 */
private suspend fun MutableList<Event>.addOverriddenInstances(
    master: Event,
    overrides: Map<RecurrenceKey, Event>,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    onOrphanOverrideDropped: (masterId: EventId, slot: RecurrenceKey) -> Unit,
) {
    if (overrides.isEmpty()) return
    val masterTiming = MasterTiming.of(master.timing, timeZone)
    val rDateKeys = master.timing.rDateKeys()

    for ((slot, override) in overrides) {
        currentCoroutineContext().ensureActive()
        if (override.status == EventStatus.CANCELLED) continue
        if (slot.isBeyondSeriesEnd(master.timing, masterTiming, rDateKeys, timeZone)) {
            onOrphanOverrideDropped(master.masterEventId, slot)
            continue
        }
        if (override.timing.overlaps(rangeStart, rangeEnd, timeZone)) this += override
    }
}

/**
 * Whether this `RECURRENCE-ID` stands for a slot the series no longer holds, as left behind by a shortening
 * that did not clean up its out-of-range overrides.
 *
 * Tested on the slot, never on the override's own `DTSTART`: an instance legitimately moved past `UNTIL` keeps
 * an in-range slot. An `RDATE` slot is never an orphan, `UNTIL` not bounding it (RFC 5545 §3.8.5.2).
 *
 * Only `UNTIL` is checked: a `COUNT` series has no bound here without replaying the rule, and a shortening
 * rewrites `UNTIL` anyway.
 */
private fun RecurrenceKey.isBeyondSeriesEnd(
    master: EventTiming,
    masterTiming: MasterTiming,
    rDateKeys: Set<RecurrenceKey>,
    timeZone: TimeZone,
): Boolean {
    val until = master.recurrenceRule?.until ?: return false
    if (this in rDateKeys) return false

    // A key whose value type contradicts the master's is unreadable here: keep it, erring on the safe side.
    val localStart = toLocalStart(master, timeZone) ?: return false
    val instantStart = if (this is Utc) instant else masterTiming.resolvedStartInstant(localStart)

    return until.isExceededBy(localStart, instantStart)
}

private fun EventTiming.rDateKeys(): Set<RecurrenceKey> {
    return if (rDates.isEmpty()) emptySet() else rDates.mapNotNullTo(HashSet()) { it.toRecurrenceKey(this) }
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

/**
 * The occurrence this `RDATE`/`EXDATE` value designates on [master], or `null` when its form can't
 * designate one. Shared with the write path so an edit re-emits a value that still matches.
 *
 * The value only carries *where* the occurrence starts; which key form identifies it is the master's
 * business, hence the delegation to [recurrenceKeyAt].
 */
internal fun IcalDateValue.toRecurrenceKey(master: EventTiming): RecurrenceKey? {
    val zone = master.startTimeZone
    val (localStart, instantStart) = when {
        // A bare DATE designates the occurrence falling on that day, which starts at the master's time.
        this is IcalDateValue.AllDay -> {
            val local = LocalDateTime(date, master.start.time)
            local to local.toInstant(zone ?: TimeZone.UTC)
        }
        // Only a DATE can designate an occurrence of an all-day master.
        master.isAllDay -> return null
        this is IcalDateValue.Floating && zone == null -> localDateTime to localDateTime.toInstant(TimeZone.UTC)
        this is IcalDateValue.Zoned && zone != null -> instant.toLocalDateTime(zone) to instant
        else -> return null
    }
    return master.recurrenceKeyAt(localStart, instantStart)
}

internal fun RecurrenceKey.toLocalStart(master: EventTiming, defaultZone: TimeZone): LocalDateTime? = when (this) {
    is AllDay -> LocalDateTime(date, master.start.time)
    is Floating -> localDateTime
    is Zoned -> if (master.startTimeZone != null) localDateTime else null
    is Utc -> instant.toLocalDateTime(master.startTimeZone ?: defaultZone)
}

/** Materialise one [occurrence] of this recurring master into a concrete synthetic [Event]. */
private fun Event.toOccurrenceEvent(occurrence: Occurrence): Event {
    // Copying keeps all master fields (title, colors, attendees, …) while overriding identity and timing.
    return copy(
        occurrenceId = OccurrenceId.Recurrence(masterEventId, occurrence.key),
        timing = timing.copy(
            start = occurrence.start,
            end = occurrence.end,
            startTimeZone = occurrence.startTimeZone,
            endTimeZone = occurrence.endTimeZone,
            isAllDay = occurrence.isAllDay,
        ),
    )
}
