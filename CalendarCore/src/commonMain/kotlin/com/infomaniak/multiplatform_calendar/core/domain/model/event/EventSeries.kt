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
 * A stored event together with the instances that redefine it, i.e. everything the expander needs to
 * materialise a series.
 *
 * Kept apart from [Event] so the public model never carries overrides: an [Event] is always a single
 * renderable thing, be it a plain event, a master or one materialised occurrence.
 *
 * [overridesByOccurrenceKey] is keyed by
 * [RecurrenceKey.canonical][com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.canonical],
 * the identity of the *theoretical* slot the override replaces, never the position it was moved to.
 * Each value is already a materialised occurrence, ready to be emitted as-is.
 */
internal data class EventSeries(
    val master: Event,
    val overridesByOccurrenceKey: Map<String, Event> = emptyMap(),
)
