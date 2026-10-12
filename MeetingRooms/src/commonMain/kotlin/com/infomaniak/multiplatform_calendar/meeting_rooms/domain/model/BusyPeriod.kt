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
package com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model

import kotlin.time.Instant

/** A period during which a meeting room is booked. */
public data class BusyPeriod(
    val start: Instant,
    val end: Instant,
    val isFullDay: Boolean,
    /** Title of the booking event, replaced by the server with a placeholder unless [isOwner]. */
    val title: String?,
    /** Email of the organizer of the booking event. */
    val organizer: String?,
    /** True when the current user organizes the booking event. */
    val isOwner: Boolean,
)
