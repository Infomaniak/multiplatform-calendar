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
import androidx.room3.Relation
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule

/** An event listed in a range, with the addresses of the user of its account. */
internal data class EventSummaryInRange(
    val id: EventId,
    val calendarColorArgb: Int?,
    @Embedded val content: EventSummaryContent,
    val rrule: RecurrenceRule?,
    val rDates: List<IcalDateValue>,
    val exDates: List<IcalDateValue>,
    @Relation(entity = EventOverrideEntity::class, parentColumns = ["id"], entityColumns = ["masterId"])
    val overrides: List<OverrideSummaryInRange> = emptyList(),
)
