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
package com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model

/**
 * A meeting room that the account can book. [uuid] is the value of the `X-INFOMANIAK-BOOKABLE` property of the events
 * booking it.
 */
public data class MeetingRoom(
    val uuid: String,
    val name: String,
    /** Hex color, `#RRGGBB` or `#RGB`. */
    val color: String,
    val description: String?,
    val location: String?,
    val floor: Int?,
    val capacity: Int?,
    val equipments: List<Equipment>,
)
