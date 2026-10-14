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

import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomColor

// `#RGB`, `#RRGGBB` or `#RRGGBBAA`, with or without `#`.
internal fun parseHexColor(hex: String?): MeetingRoomColor? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    val rrggbbaa = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("") + "FF"
        6 -> digits + "FF"
        8 -> digits
        else -> return null
    }
    val rgba = try {
        rrggbbaa.hexToUInt()
    } catch (_: IllegalArgumentException) {
        return null
    }
    return MeetingRoomColor(rgba.rotateRight(8).toInt())
}
