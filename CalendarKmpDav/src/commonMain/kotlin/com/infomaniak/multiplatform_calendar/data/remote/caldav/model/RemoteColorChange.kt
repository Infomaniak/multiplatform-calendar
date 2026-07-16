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
package com.infomaniak.multiplatform_calendar.data.remote.caldav.model

/**
 * Requested change to a VEVENT's color (RFC 7986 `COLOR` and/or Apple's `X-APPLE-CALENDAR-COLOR`).
 * Mirrors the Rust `ColorChange` enum at the FFI boundary.
 */
sealed interface RemoteColorChange {
    /** Leave both properties as they are (no color emitted on create). */
    data object Unchanged : RemoteColorChange

    /** Write `X-APPLE-CALENDAR-COLOR:[hex]` and drop any pre-existing `COLOR:<name>`. */
    data class Set(val hex: String) : RemoteColorChange

    data object Cleared : RemoteColorChange
}
