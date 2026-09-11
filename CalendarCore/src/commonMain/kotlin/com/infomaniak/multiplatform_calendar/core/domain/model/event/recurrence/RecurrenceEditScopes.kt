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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.EventRecurrenceState.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope.AllOccurrences
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope.ThisAndFollowing
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope.ThisOccurrence

/**
 * Which scopes a user may pick from before deleting. An empty set means there is nothing to ask.
 *
 * Only an [Occurrence] offers a choice: a master is reached by its id rather than by picking a date,
 * so it holds no occurrence to delete "this one" of.
 *
 * [ThisAndFollowing] is offered even on the first occurrence, where it does what [AllOccurrences]
 * does: telling those apart needs the series start, which a windowed read cannot recover.
 */
internal fun deleteScopesFor(
    recurrence: EventRecurrenceState,
    canEdit: Boolean,
): Set<RecurrenceEditScope> = when {
    !canEdit -> emptySet()
    recurrence != Occurrence -> emptySet()
    else -> setOf(ThisOccurrence, ThisAndFollowing, AllOccurrences)
}
