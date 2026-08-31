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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.TimeBlocking
import kotlinx.datetime.LocalDateTime

/**
 * The content of a `VEVENT`, i.e. everything but its server identity and its recurrence rules.
 *
 * A `RECURRENCE-ID` override redefines the very same properties, so [EventEntity] and
 * [EventOverrideEntity] both embed this block rather than each declaring their own columns.
 */
internal data class EventContentEntity(
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    @Embedded val timing: EventTimingEntity,
    val created: LocalDateTime? = null,
    val lastModified: LocalDateTime? = null,
    val dtStamp: LocalDateTime? = null,
    val status: EventStatus? = null,
    val timeBlocking: TimeBlocking? = null,
    val classification: Classification? = null,
    val priority: Int? = null,
    val sequence: Int? = null,
    val categories: List<String>? = null,
    val attendees: List<AttendeeEntity> = emptyList(),
    val organizer: OrganizerEntity? = null,
    val alarms: List<AlarmEntity> = emptyList(),
    /** Packed ARGB, or `null` when the event inherits its calendar's color. */
    val colorArgb: Int? = null,
    /** Original RFC 7986 `COLOR:<name>` kept verbatim so untouched-color edits round-trip byte-exact. */
    val colorIcalName: String? = null,
)
