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

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlinx.datetime.LocalDateTime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalObjCRefinement::class)
data class Event(
    @HiddenFromObjC
    val id: EventId,
    @HiddenFromObjC
    val calendarId: CalendarId,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val status: String? = null,
    val categories: String? = null,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val isAllDay: Boolean = false,
    val recurrenceRule: RecurrenceRule? = null,
    val etag: String? = null,
    val rawIcs: String? = null,
    val lastModified: LocalDateTime? = null,
    val isSynced: Boolean = false,
    val attendees: List<Attendee> = emptyList(),
    val organizer: Attendee? = null,
)
