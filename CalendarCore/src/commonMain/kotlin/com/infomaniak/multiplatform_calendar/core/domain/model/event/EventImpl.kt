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

import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal data class EventImpl(
    override val id: EventId,
    override val calendarId: CalendarId,
    override val accountId: AccountId,
    override val title: String,
    override val description: String? = null,
    override val location: String? = null,
    override val status: EventStatus? = null,
    override val classification: Classification? = null,
    override val categories: List<String> = emptyList(),
    override val timing: EventTiming,
    override val lastModified: Instant? = null,
    override val attendees: List<Attendee> = emptyList(),
    override val organizer: Attendee? = null,
    override val calendarColor: CalendarColor,
    override val colors: EventColors,
    override val canEdit: Boolean,
) : Event
