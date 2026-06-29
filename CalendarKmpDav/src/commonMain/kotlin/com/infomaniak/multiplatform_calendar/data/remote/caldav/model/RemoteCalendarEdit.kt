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
 * Edited fields applied onto a CalDAV calendar collection by [CalendarSyncRemoteSource.updateCalendar].
 *
 * Each `null` field is left untouched on the server; only non-null values are sent in the PROPPATCH.
 * Colors follow the CalDAV format (`#RRGGBB` or `#RRGGBBAA`).
 */
data class RemoteCalendarEdit(
    val displayName: String? = null,
    val color: String? = null,
) {
    val hasChanges: Boolean get() = displayName != null || color != null
}
