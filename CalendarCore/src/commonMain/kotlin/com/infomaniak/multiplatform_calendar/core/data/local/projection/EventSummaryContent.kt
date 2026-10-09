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
package com.infomaniak.multiplatform_calendar.core.data.local.projection

import androidx.room3.Embedded
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus

/** The columns of an event or override row read for the event lists and the upcoming alarms. */
internal data class EventSummaryContent(
    val summary: String,
    val location: String?,
    @Embedded val timing: EventTimingEntity,
    val status: EventStatus?,
    val attendees: List<AttendeeEntity>,
    val colorArgb: Int?,
    val meetRoomUrl: String?,
    val bookableUuid: String?,
    val alarms: List<AlarmEntity>,
)
