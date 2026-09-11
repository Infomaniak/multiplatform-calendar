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
 * Overrides to drop while the master is patched (RFC 5545 §3.8.4.4).
 * Mirrors the Rust `OverrideRemoval` enum at the FFI boundary.
 *
 * Deleting an instance that carries an override means dropping its VEVENT too, otherwise the
 * resource keeps an occurrence no rule generates any more.
 */
sealed interface RemoteOverrideRemoval {
    /** Leave every override in place. */
    data object Unchanged : RemoteOverrideRemoval

    /** Drop the override of each of [recurrenceIds]; instances with no override are ignored. */
    data class Instances(val recurrenceIds: List<RemoteRecurrenceId>) : RemoteOverrideRemoval
}
