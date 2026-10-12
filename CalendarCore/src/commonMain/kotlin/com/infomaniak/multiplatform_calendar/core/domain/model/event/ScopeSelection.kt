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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope

/**
 * What a mutation of an [Event] may be aimed at, read from [Event.scopeSelection]. Common to updates
 * and deletes; whether the event may be mutated at all is [Event.canEdit].
 */
public sealed interface ScopeSelection {

    /** A plain event, or a series named by its master: nothing to ask, the mutation goes to [target]. */
    @ConsistentCopyVisibility
    public data class Single internal constructor(val target: OccurrenceTarget.Single) : ScopeSelection

    /**
     * An occurrence: the user picks one of [options], one per [RecurrenceScope], in display order.
     *
     * [RecurrenceScope.ThisAndFollowing] is offered even on the first occurrence, where it does what
     * [RecurrenceScope.AllOccurrences] does: telling those apart needs the series start, which a
     * windowed read cannot recover.
     */
    @ConsistentCopyVisibility
    public data class Recurring internal constructor(val options: List<OccurrenceTarget.Recurring>) : ScopeSelection
}
