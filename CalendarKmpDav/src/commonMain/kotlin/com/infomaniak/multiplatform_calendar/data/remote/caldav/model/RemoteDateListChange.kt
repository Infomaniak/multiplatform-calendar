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
 * Requested change to a recurrence master's `EXDATE` or `RDATE` set (RFC 5545 §3.8.5.1/§3.8.5.2).
 * Mirrors the Rust `DateListChange` enum at the FFI boundary.
 */
sealed interface RemoteDateListChange {
    /** Leave the existing lines untouched (so unmodelled values survive a partial edit; none emitted on create). */
    data object Unchanged : RemoteDateListChange

    /** Replace every existing line with [lines]; an empty list is equivalent to [Cleared]. */
    data class Set(val lines: List<RemoteDateListLine>) : RemoteDateListChange

    /** Drop every existing line of that property. */
    data object Cleared : RemoteDateListChange
}

/**
 * One `EXDATE`/`RDATE` line. A line carries a single `TZID` and a single value type (RFC 5545 §3.2.19),
 * so values of differing forms or zones must be split across several lines.
 *
 * [tzid] is `null` for all-day, floating and UTC (`Z`-suffixed) values; [isDateOnly] emits `VALUE=DATE`
 * and takes precedence over [tzid].
 */
data class RemoteDateListLine(
    val tzid: String?,
    val isDateOnly: Boolean,
    val values: List<String>,
)
