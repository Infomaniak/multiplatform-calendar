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
package com.infomaniak.multiplatform_calendar.core.utils

import com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.local.entity.toEventAndRawIcsEntities

internal suspend fun EventDao.upsert(events: List<EventWithRawIcs>) {
    val (eventEntities, rawIcsEntities) = events.toEventAndRawIcsEntities()
    upsertEventsWithRawIcs(eventEntities, rawIcsEntities)
}

/** Seeds events with empty raw ICS for tests that only care about the event rows. */
internal suspend fun EventDao.seedEvents(events: List<EventEntity>) {
    upsert(events.map { EventWithRawIcs(it, "") })
}
