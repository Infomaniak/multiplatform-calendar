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
package com.infomaniak.multiplatform_calendar.core.domain.model.calendar

/**
 * Access level the current user holds on a [Calendar], resolved from the
 * CalDAV `current-user-privilege-set` (RFC 3744).
 */
public enum class CalendarAccessLevel {
    /** No privilege granted on the collection. */
    NONE,

    /** Read-only access. */
    READ,

    /** Read and write access. */
    READ_WRITE,

    /** Full control: the principal owns the collection. */
    OWNER;

    /** Whether the current user can create/update/delete events in this calendar. */
    public val canWrite: Boolean get() = this == READ_WRITE || this == OWNER

    /** Whether the calendar is visible but not editable by the current user. */
    public val isReadOnly: Boolean get() = !canWrite
}

