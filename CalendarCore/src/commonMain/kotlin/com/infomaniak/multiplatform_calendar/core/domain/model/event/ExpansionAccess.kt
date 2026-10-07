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

/**
 * How the recurrence expansion reads the events of type [T] it expands, and copies a master into one of its
 * occurrences.
 */
internal class ExpansionAccess<T>(
    val idOf: (T) -> EventId,
    val timingOf: (T) -> EventTiming,
    val isCancelled: (T) -> Boolean,
    val occurrenceOf: (master: T, occurrenceId: OccurrenceId.Recurrence, bounds: EventBounds) -> T,
)

internal val EventExpansionAccess = ExpansionAccess<Event>(
    idOf = Event::masterEventId,
    timingOf = Event::timing,
    isCancelled = { it.status == EventStatus.CANCELLED },
    occurrenceOf = { master, occurrenceId, bounds ->
        master.copy(occurrenceId = occurrenceId, timing = master.timing.copy(bounds = bounds))
    },
)
