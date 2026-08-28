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
package com.infomaniak.multiplatform_calendar.core.data.local.relation

import androidx.room3.Embedded
import androidx.room3.Relation
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity

/**
 * An event together with its parent calendar, fetched atomically via Room relation queries.
 *
 * [overrides] is empty for anything but a recurring master. Room resolves it with a single batched
 * `IN (…)` query over every event of the outer result, so expanding N masters costs one query, not N.
 */
internal data class EventWithCalendarEntity(
    @Embedded val event: EventEntity,
    @Relation(parentColumns = ["calendarId"], entityColumns = ["id"])
    val calendar: CalendarEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["masterId"])
    val overrides: List<EventOverrideEntity> = emptyList(),
)

