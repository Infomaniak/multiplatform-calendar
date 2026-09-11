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
 * The `RECURRENCE-ID` of the instance an override stands for (RFC 5545 §3.8.4.4).
 * Mirrors the Rust `RecurrenceIdSpec` record at the FFI boundary.
 *
 * Its value type must match the master's `DTSTART`, so it is described exactly like one
 * [RemoteDateListLine] value: [tzid] is `null` for all-day, floating and UTC (`Z`-suffixed) values,
 * and [isDateOnly] emits `VALUE=DATE` and takes precedence over [tzid].
 */
data class RemoteRecurrenceId(
    val tzid: String?,
    val isDateOnly: Boolean,
    val value: String,
)
