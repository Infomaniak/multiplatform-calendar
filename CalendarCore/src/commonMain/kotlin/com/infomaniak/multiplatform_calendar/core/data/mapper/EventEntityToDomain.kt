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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventImpl
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal fun EventEntity.toDomain(calendar: Calendar): Event {
    val attendees = attendees.map(AttendeeEntity::toDomain)
    return EventImpl(
        id = id,
        calendarId = calendarId,
        accountId = calendar.accountId,
        title = summary,
        description = description?.ifBlank { null },
        location = location?.ifBlank { null },
        status = status,
        classification = classification,
        categories = categories?.filter { it.isNotBlank() }.orEmpty(),
        timing = timing.toDomain(),
        lastModified = lastModified?.toInstant(TimeZone.UTC),
        attendees = attendees,
        organizer = attendees.firstOrNull { it.isOrganizer },
        colors = EventColors.from(calendar.colors),
        canEdit = calendar.accessLevel.canWrite,
    )
}
