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
package com.infomaniak.multiplatform_calendar.core.data.local

import androidx.room.TypeConverter
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Duration

internal class CalendarTypeConverters {

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun fromDuration(value: Duration?): String? = value?.toIsoString()

    @TypeConverter
    fun toDuration(value: String?): Duration? = value?.let { Duration.parseIsoString(it) }

    @TypeConverter
    fun fromAttendees(value: List<AttendeeEntity>): String = Json.encodeToString(value)

    @TypeConverter
    fun toAttendees(value: String): List<AttendeeEntity> = Json.decodeFromString(value)

    @TypeConverter
    fun fromClassification(value: Classification?): String? = value?.toIcalString()

    @TypeConverter
    fun toClassification(value: String?): Classification? = Classification.fromIcalString(value)

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.let { Json.decodeFromString(it) }
}
