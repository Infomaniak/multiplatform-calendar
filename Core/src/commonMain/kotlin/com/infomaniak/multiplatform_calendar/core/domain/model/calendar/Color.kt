/*
 * Infomaniak Core - Android
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.core.domain.model.calendar

data class Color(
    private val red: Int,
    private val green: Int,
    private val blue: Int,
    private val alpha: Int,
) {
    fun toLong(): Long =
        ((alpha.toLong() and 0xFF) shl 24) or
        ((red.toLong() and 0xFF) shl 16) or
        ((green.toLong() and 0xFF) shl 8) or
        (blue.toLong() and 0xFF)

    companion object {
        fun fromLong(value: Long): Color = Color(
            red = ((value shr 16) and 0xFF).toInt(),
            green = ((value shr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
            alpha = ((value shr 24) and 0xFF).toInt(),
        )
    }
}
