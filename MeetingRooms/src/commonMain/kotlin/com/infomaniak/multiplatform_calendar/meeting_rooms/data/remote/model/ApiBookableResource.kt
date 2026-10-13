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
package com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model

import kotlinx.serialization.Serializable

/** Bookable resource as returned by GET /pim/bookable-resources/all and /{uuid}. */
@Serializable
internal data class ApiBookableResource(
    val uuid: String,
    val name: String,
    val color: String? = null,
    /** `meeting_room`, `accessory` or `internet`. */
    val category: String,
    val description: String? = null,
    val location: String? = null,
    val floor: Int? = null,
    val capacity: Int? = null,
    val equipments: List<ApiEquipment> = emptyList(),
) {
    val isMeetingRoom: Boolean get() = category == MEETING_ROOM_CATEGORY
}

private const val MEETING_ROOM_CATEGORY = "meeting_room"
