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
package com.infomaniak.multiplatform_calendar.core.data.remote.model

/** Hexadecimal CalDAV color: `#RRGGBB` or `#RRGGBBAA`, with or without `#`. */
private val HEX_COLOR_REGEX = Regex("^#?([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})?$")

/**
 * Parse a hexadecimal CalDAV color (`#RRGGBBAA` or `#RRGGBB`) into an ARGB [Long] value.
 *
 * Returns `null` if the input is missing or malformed.
 *
 * We keep a plain [Long] value on the remote/persistence side: the conversion to the domain
 * model `Color` only happens when going from DB to domain via `Color.fromLong`.
 */
internal fun parseHexColor(hex: String?): Long? {
    if (hex == null) return null

    val match = HEX_COLOR_REGEX.matchEntire(hex.trim()) ?: return null
    val (r, g, b, a) = match.destructured

    val red = r.toLong(16)
    val green = g.toLong(16)
    val blue = b.toLong(16)
    val alpha = if (a.isEmpty()) 0xFFL else a.toLong(16)

    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

