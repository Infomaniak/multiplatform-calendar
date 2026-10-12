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
package com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Booking returned by POST /pim/event/bookable-availability; [start] and [end] are ISO 8601 with an offset. */
@Serializable
internal data class ApiBusyPeriod(
    val start: String,
    val end: String,
    val fullday: Boolean = false,
    val title: String? = null,
    val organizer: String? = null,
    @SerialName("is_owner") val isOwner: Boolean = false,
)
