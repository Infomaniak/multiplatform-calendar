/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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

import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.EventRecurrenceState
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.deleteScopesFor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.hasRecurrenceSet
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalObjCRefinement::class)
public data class Event(
    @HiddenFromObjC
    val masterEventId: EventId,
    @HiddenFromObjC
    val occurrenceId: OccurrenceId,
    @HiddenFromObjC
    val calendarId: CalendarId,
    @HiddenFromObjC
    val accountId: AccountId,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val status: EventStatus? = null,
    val timeBlocking: TimeBlocking? = null,
    val classification: Classification? = null,
    val categories: List<String> = emptyList(),
    val timing: EventTiming,
    val lastModified: Instant? = null,
    val attendees: List<Attendee> = emptyList(),
    val organizer: Organizer? = null,
    val colors: EventColors,
    val canEdit: Boolean,
    val alarms: List<EventAlarm> = emptyList(),
) {
    /**
     * Derived rather than stored, because an occurrence is built by copying its master and swapping
     * [occurrenceId] and [timing]: a stored value would keep saying `Master` on every occurrence.
     *
     * [timing] alone cannot answer this. An occurrence keeps the rule it was generated from, and an
     * override carries none at all, so both would be misread. [occurrenceId] is what actually knows.
     */
    val recurrence: EventRecurrenceState
        get() = when {
            occurrenceId is OccurrenceId.Recurrence -> EventRecurrenceState.Occurrence
            timing.hasRecurrenceSet() -> EventRecurrenceState.Master
            else -> EventRecurrenceState.None
        }

    /** What deleting this event may be asked to reach, empty when there is nothing to ask. */
    val deleteScopes: Set<RecurrenceEditScope>
        get() = deleteScopesFor(recurrence, canEdit)
}
