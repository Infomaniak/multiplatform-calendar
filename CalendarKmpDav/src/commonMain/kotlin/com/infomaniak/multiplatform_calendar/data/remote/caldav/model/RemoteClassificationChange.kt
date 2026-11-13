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
 * Requested change to a VEVENT's access classification (`CLASS`, RFC 5545 §3.8.1.3).
 * Mirrors the Rust `ClassificationChange` enum at the FFI boundary.
 */
sealed interface RemoteClassificationChange {
    /** Leave `CLASS` as it is, whatever its value (none emitted on create). */
    data object Unchanged : RemoteClassificationChange

    /** Write `CLASS:[value]`, replacing any existing one. */
    data class Set(val value: String) : RemoteClassificationChange

    /** Drop `CLASS`, which then reads as its RFC default, `PUBLIC`. */
    data object Cleared : RemoteClassificationChange
}
