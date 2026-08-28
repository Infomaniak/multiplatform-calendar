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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId

/**
 * An override is a whole occurrence on its own, so it maps to the same synthetic [Event] the expander
 * would have produced for that slot — same `masterId#recurrenceKey` identity, but the server's own
 * content and position. Its recurrence stays empty: only the master carries the rule.
 */
internal fun EventOverrideEntity.toDomain(
    calendar: Calendar,
): Event = content.toDomain(
    masterEventId = masterId,
    occurrenceId = OccurrenceId.of(masterId, recurrenceKey),
    calendar = calendar,
)
