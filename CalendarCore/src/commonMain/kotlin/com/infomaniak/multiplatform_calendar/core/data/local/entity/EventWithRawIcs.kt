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

/**
 * An event paired with its raw ICS text. Both live in separate tables ([EventEntity] and
 * [EventRawIcsEntity]) but must always be written together, so upserts take this pair to keep
 * the two rows coordinated.
 */
internal data class EventWithRawIcs(
    val event: EventEntity,
    val rawIcs: String,
)

internal fun List<EventWithRawIcs>.partitionEntities(): Pair<List<EventEntity>, List<EventRawIcsEntity>> {
    return map { it.event to EventRawIcsEntity(eventId = it.event.id, rawIcs = it.rawIcs) }.unzip()
}
