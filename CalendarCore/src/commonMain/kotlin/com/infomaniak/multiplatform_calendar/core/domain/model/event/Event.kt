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
    val classification: Classification? = null,
    val categories: List<String> = emptyList(),
    val timing: EventTiming,
    val lastModified: Instant? = null,
    val attendees: List<Attendee> = emptyList(),
    val organizer: Organizer? = null,
    val colors: EventColors,
    val canEdit: Boolean,
    val alarms: List<EventAlarm> = emptyList(),
)
