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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionLimits
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.RecurrenceExpander
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.TimeZone
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
 * RRULE only for now: `RDATE` / `EXDATE` / overrides come in later PRs.
 *
 * [onExpansionTruncated] is invoked with the master's [EventId] whenever the expander stops on a safety cap
 * (outcome other than [ExpansionOutcome.Completed]) so the caller can surface it (e.g. to Sentry); the
 * partial occurrences gathered so far are still returned.
 */
internal suspend fun List<Event>.expandRecurrencesInWindow(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
    onExpansionTruncated: (masterId: EventId, outcome: ExpansionOutcome) -> Unit = { _, _ -> },
): List<Event> {
    val expanded = ArrayList<Event>(size)
    val occurrences = ArrayList<Occurrence>()
    for (event in this) {
        currentCoroutineContext().ensureActive()
        val rrule = event.timing.recurrenceRule
        if (rrule == null) {
            expanded += event
            continue
        }
        occurrences.clear()
        val outcome = RecurrenceExpander.expandInto(
            target = occurrences,
            master = event.timing,
            rrule = rrule,
            inputStart = rangeStart,
            inputEnd = rangeEnd,
            defaultZone = timeZone,
            limits = limits,
        )
        if (outcome != ExpansionOutcome.Completed) onExpansionTruncated(event.masterEventId, outcome)
        for (occurrence in occurrences) expanded += event.toOccurrenceEvent(occurrence)
    }
    return expanded
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
