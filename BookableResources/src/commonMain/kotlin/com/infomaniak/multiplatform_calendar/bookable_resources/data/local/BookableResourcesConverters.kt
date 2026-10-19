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
package com.infomaniak.multiplatform_calendar.bookable_resources.data.local

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.EquipmentEntity

/** Tolerates fields written by an older schema, since the JSON is persisted. */
private val json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public class BookableResourcesConverters {

    @ColumnTypeConverter
    public fun fromEquipments(equipments: List<EquipmentEntity>): String = json.encodeToString(equipments)

    @ColumnTypeConverter
    public fun toEquipments(value: String): List<EquipmentEntity> = json.decodeFromString(value)
}
