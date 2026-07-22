/*
 * Infomaniak Core - Android
 * Copyright (C) 2026-2026 Infomaniak Network SA
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

package com.infomaniak.multiplatform_calendar.core.domain.model.event

/**
 * The ORGANIZER of an event (RFC 5545 §3.8.4.3). Distinct from an [Attendee]: an organizer is not
 * necessarily a participant, so it carries no participation status, role or RSVP.
 */
public data class Organizer(
    val email: String,
    val displayName: String? = null,
)
