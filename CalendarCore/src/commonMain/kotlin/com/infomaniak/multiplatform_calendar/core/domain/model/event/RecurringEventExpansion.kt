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
 * Each occurrence is a synthetic [Event] whose id is `masterId + "#" + canonicalRecurrenceKey`
 * (stable per instance) and whose timing is the occurrence's own (wall-clock preserved across DST,
 * `end` exclusive). The master's `RRULE` is kept on the instance's timing so consumers can still tell
 * it belongs to a series — the expander is never re-run on an already-materialised occurrence.
 *
 * RRULE only for now: `RDATE` / `EXDATE` / overrides come in later PRs.
 */
internal suspend fun List<Event>.expandRecurrencesInWindow(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
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
        RecurrenceExpander.expandInto(
            target = occurrences,
            master = event.timing,
            rrule = rrule,
            inputStart = rangeStart,
            inputEnd = rangeEnd,
            defaultZone = timeZone,
            limits = limits,
        )
        for (occurrence in occurrences) expanded += event.toOccurrenceEvent(occurrence)
    }
    return expanded
}

/** Materialise one [occurrence] of this recurring master into a concrete synthetic [Event]. */
private fun Event.toOccurrenceEvent(occurrence: Occurrence): Event {
    // Copying keeps all master fields (title, colors, attendees, …) while overriding identity and timing.
    return copy(
        id = EventId("${id.url}#${occurrence.key.canonical}"),
        timing = timing.copy(
            start = occurrence.start,
            end = occurrence.end,
            startTimeZone = occurrence.startTimeZone,
            endTimeZone = occurrence.endTimeZone,
            isAllDay = occurrence.isAllDay,
        ),
    )
}
