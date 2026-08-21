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
package com.infomaniak.multiplatform_calendar.core.data.local.entity

/** The three tables a synced event spreads over, ready to be written in a single transaction. */
internal data class EventUpsertBatch(
    val events: List<EventEntity>,
    val rawIcs: List<EventRawIcsEntity>,
    val overrides: List<EventOverrideEntity>,
)

internal fun List<EventWithRawIcs>.toUpsertBatch(): EventUpsertBatch {
    val events = ArrayList<EventEntity>(size)
    val rawIcs = ArrayList<EventRawIcsEntity>(size)
    val overrides = ArrayList<EventOverrideEntity>()
    for (item in this) {
        events += item.event
        rawIcs += EventRawIcsEntity(eventId = item.event.id, rawIcs = item.rawIcs)
        overrides += item.overrides
    }
    return EventUpsertBatch(events, rawIcs, overrides)
}
