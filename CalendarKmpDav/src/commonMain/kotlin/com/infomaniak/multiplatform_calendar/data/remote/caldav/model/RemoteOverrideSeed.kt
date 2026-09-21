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
 * Where a carried override takes its content from: the resource [icsData] it currently lives in, and
 * the [recurrenceId] designating it there. Mirrors the Rust `OverrideSeed` record at the FFI boundary.
 *
 * Splitting a series moves its future overrides onto the new tail. Rebuilding them from the edit model
 * alone would drop whatever it cannot represent — `STATUS:CANCELLED` above all, which would bring a
 * cancelled occurrence back to life — so they are cloned from their own VEVENT instead.
 */
data class RemoteOverrideSeed(
    val icsData: String,
    val recurrenceId: RemoteRecurrenceId,
)
