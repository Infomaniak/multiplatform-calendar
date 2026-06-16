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

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("calendarId"), Index("dtStart"), Index("dtEndEffective")],
)
internal data class EventEntity(
    @PrimaryKey val id: EventId,
    val calendarId: CalendarId,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val dtStart: LocalDateTime,
    val dtEnd: LocalDateTime? = null,
    val duration: Duration? = null,
    // Resolved end (single source of truth, see `resolveEffectiveEnd`): used for range-overlap queries
    // (see [EventDao.observeVisibleInRange]) and as the domain timing's end. SQL can't derive it from ISO-text storage.
    val dtEndEffective: LocalDateTime,
    val created: LocalDateTime? = null,
    val lastModified: LocalDateTime? = null,
    val dtStamp: LocalDateTime? = null,
    val isAllDay: Boolean = false,
    val rrule: String? = null,
    val status: String? = null,
    val transp: String? = null,
    val classification: String? = null,
    val priority: Int? = null,
    val sequence: Int? = null,
    val categories: String? = null,
    val organizer: String? = null,
    val etag: String,
    val rawIcs: String,
    val isSynced: Boolean = false,
)
