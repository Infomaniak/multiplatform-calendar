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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventSourceColor

/**
 * This event as the edit that would leave it exactly as it stands, for occurrence-level operations
 * which touch a series' recurrence set without editing the event itself.
 *
 * Every field has to be carried over: [toRemoteEdit] reads an edit as the intended *final* state and
 * emits a change for whatever differs, so an omission here would not preserve a field — it would
 * clear it. The recurrence rule and its dates are part of that, hence [EventEntity.rrule] and its
 * date lists travelling in the timing.
 */
internal fun EventEntity.toEditData(): EventEditData = EventEditData(
    title = content.summary,
    timing = content.timing.toDomain(recurrenceRule = rrule, rDates = rDates, exDates = exDates),
    location = content.location,
    description = content.description,
    timeBlocking = content.timeBlocking,
    calendarId = calendarId,
    eventColor = content.colorArgb?.let(::EventSourceColor),
    alarms = content.alarms.mapNotNull(AlarmEntity::toDomain),
)
