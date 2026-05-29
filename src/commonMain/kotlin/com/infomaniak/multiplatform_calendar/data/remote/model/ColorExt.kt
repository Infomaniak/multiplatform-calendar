/*
 * Infomaniak Calendar - Multiplatform
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
package com.infomaniak.multiplatform_calendar.data.remote.model

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Color

/** Parse CalDAV hex color string (#RRGGBBAA or #RRGGBB) into domain Color. */
internal fun parseHexColor(hex: String): Color {
    val clean = hex.trimStart('#')
    val r = clean.substring(0, 2).toInt(16)
    val g = clean.substring(2, 4).toInt(16)
    val b = clean.substring(4, 6).toInt(16)
    val a = if (clean.length >= 8) clean.substring(6, 8).toInt(16) else 255
    return Color(red = r, green = g, blue = b, alpha = a)
}
