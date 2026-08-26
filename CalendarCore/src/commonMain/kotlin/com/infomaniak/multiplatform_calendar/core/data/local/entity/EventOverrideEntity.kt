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

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import kotlinx.datetime.LocalDateTime

/**
 * A `VEVENT` overriding a single instance of a recurring series (RFC 5545 §3.8.4.4).
 *
 * Kept in its own table so the range scan only ever returns masters: rendering an instance requires
 * its master's context. Both positions matter to that scan — the **original** one below, which must
 * be hidden wherever the series would have expanded it, and the **effective** one carried by
 * [EventContentEntity.timing], where the instance actually lands once moved.
 */
@Entity(
    tableName = "event_overrides",
    primaryKeys = ["masterId", "recurrenceKey"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["masterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("originalStartInstantMs"),
        Index("originalEndInstantMs"),
        Index("dtStartInstantMs"),
        Index("dtEndInstantMs"),
    ],
)
internal data class EventOverrideEntity(
    val masterId: EventId,
    /** Identity of the overridden instance, never the override's own (possibly moved) `DTSTART`. */
    val recurrenceKey: RecurrenceKey,
    /** `null` for floating masters, see [EventTimingEntity.dtStartInstantMs]. */
    val originalStartInstantMs: Long?,
    val originalEndInstantMs: Long?,
    /** Used by the floating/all-day range branch, which cannot rely on the instants above. */
    val originalStartLocalDateTime: LocalDateTime,
    val originalEndLocalDateTime: LocalDateTime,
    @Embedded val content: EventContentEntity,
)
