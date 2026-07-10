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
 * Partial update to apply to a [Calendar].
 *
 * Each property is nullable: `null` means *leave it unchanged*; a non-null value means *set it to this*.
 * [displayName] and [color] are propagated to the server via a CalDAV PROPPATCH; [isVisible] is a
 * local-only flag and is never sent over the wire.
 */
public data class CalendarEditData(
    val displayName: String? = null,
    val color: CalendarSourceColor? = null,
    val isVisible: Boolean? = null,
) {
    internal val hasAnyChanges = hasLocalChanges || hasRemoteChanges
    internal val hasRemoteChanges: Boolean get() = displayName != null || color != null
    private val hasLocalChanges: Boolean get() = isVisible != null
}
