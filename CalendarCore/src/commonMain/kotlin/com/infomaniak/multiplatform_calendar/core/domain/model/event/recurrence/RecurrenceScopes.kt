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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope.AllOccurrences
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope.ThisAndFollowing
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope.ThisOccurrence

/**
 * Which scopes a user may pick from before mutating an event, empty when there is nothing to ask.
 * Common to updates and deletes: a scope says how far a mutation reaches, not which one it is.
 *
 * Only an occurrence offers a choice, a master being reached by its id rather than by picking a date.
 *
 * [ThisAndFollowing] is offered even on the first occurrence, where it does what [AllOccurrences]
 * does: telling those apart needs the series start, which a windowed read cannot recover.
 */
internal fun recurrenceScopesFor(
    isOccurrence: Boolean,
    canEdit: Boolean,
): Set<RecurrenceScope> = when {
    !canEdit -> emptySet()
    !isOccurrence -> emptySet()
    else -> setOf(ThisOccurrence, ThisAndFollowing, AllOccurrences)
}

/** [recurrenceScopesFor] minus [ThisAndFollowing], which no edit honours until a split exists. */
internal fun editScopesFor(
    isOccurrence: Boolean,
    canEdit: Boolean,
): Set<RecurrenceScope> = recurrenceScopesFor(isOccurrence, canEdit) - ThisAndFollowing
