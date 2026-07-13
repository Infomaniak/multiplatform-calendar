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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import kotlinx.datetime.LocalDateTime

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
    indices = [Index("calendarId"), Index("dtStartInstantMs"), Index("dtEndInstantMs")],
)
internal data class EventEntity(
    @PrimaryKey val id: EventId,
    val calendarId: CalendarId,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    @Embedded val timing: EventTimingEntity,
    val created: LocalDateTime? = null,
    val lastModified: LocalDateTime? = null,
    val dtStamp: LocalDateTime? = null,
    val rrule: String? = null,
    val status: EventStatus? = null,
    val transp: String? = null,
    val classification: Classification? = null,
    val priority: Int? = null,
    val sequence: Int? = null,
    val categories: List<String>? = null,
    val attendees: List<AttendeeEntity> = emptyList(),
    val etag: String,
    /** Packed ARGB, or `null` when the event inherits its calendar's color. */
    val colorArgb: Int? = null,
    /** Original RFC 7986 `COLOR:<name>` kept verbatim so untouched-color edits round-trip byte-exact. */
    val colorIcalName: String? = null,
    // The raw ICS text of the event, as returned by the CalDAV server. This is used for syncing and for editing events.
    val rawIcs: String,
    val isSynced: Boolean = false,
)
