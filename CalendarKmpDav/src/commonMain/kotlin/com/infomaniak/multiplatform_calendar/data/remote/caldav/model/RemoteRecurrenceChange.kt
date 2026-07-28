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
 * Requested change to a recurrence master's `RRULE` (RFC 5545 §3.8.5.3).
 * Mirrors the Rust `RecurrenceChange` enum at the FFI boundary.
 */
sealed interface RemoteRecurrenceChange {
    /** Leave any existing `RRULE` untouched (so an unmodelled rule survives a partial edit; none emitted on create). */
    data object Unchanged : RemoteRecurrenceChange

    /** Write `RRULE:[value]` on the master, replacing any pre-existing one. [value] excludes the `RRULE:` prefix. */
    data class Set(val value: String) : RemoteRecurrenceChange

    /** Drop any existing `RRULE`, turning the event into a single (non-recurring) occurrence. */
    data object Cleared : RemoteRecurrenceChange
}
