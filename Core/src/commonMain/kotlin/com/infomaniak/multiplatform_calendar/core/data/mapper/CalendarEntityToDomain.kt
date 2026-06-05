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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColor

internal fun CalendarEntity.toDomain() = Calendar(
    id = id,
    accountId = accountId,
    displayName = displayName,
    color = color?.let(CalendarColor::fromLong) ?: CalendarColor(red = 0x21, green = 0x96, blue = 0xF3, alpha = 0xFF),
    isVisible = isVisible,
    accessLevel = accessLevel,
)

