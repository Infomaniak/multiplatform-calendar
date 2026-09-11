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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.EventRecurrenceState.Master
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.EventRecurrenceState.None
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.EventRecurrenceState.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope.AllOccurrences
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope.ThisAndFollowing
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope.ThisOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceEditScopesTest {

    @Test
    fun deleteScopes_matchTheirState() {
        val cases = mapOf(
            None to emptySet(),
            Master to emptySet(),
            Occurrence to setOf(ThisOccurrence, ThisAndFollowing, AllOccurrences),
        )

        cases.forEach { (recurrence, expected) ->
            assertEquals(expected, deleteScopesFor(recurrence, canEdit = true), "for $recurrence")
        }
    }

    @Test
    fun deleteScopes_areEmptyOnAReadOnlyCalendar() {
        EventRecurrenceState.entries.forEach { recurrence ->
            assertEquals(emptySet(), deleteScopesFor(recurrence, canEdit = false), "for $recurrence")
        }
    }

    @Test
    fun deleteScopes_coverEveryState() {
        // A new state must be given a row above, not fall into the "not an occurrence" branch.
        assertEquals(setOf(None, Master, Occurrence), EventRecurrenceState.entries.toSet())
    }
}
