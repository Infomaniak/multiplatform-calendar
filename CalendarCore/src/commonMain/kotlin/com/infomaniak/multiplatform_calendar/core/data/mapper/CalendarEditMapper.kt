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
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toCaldavHex
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSourceColor
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarEditData
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteCalendarEdit

internal fun CalendarEditData.toRemoteEdit() = RemoteCalendarEdit(
    displayName = displayName,
    color = color?.toCaldavHex(),
)

internal fun CalendarEntity.applyEdit(edit: CalendarEditData): CalendarEntity = copy(
    displayName = edit.displayName ?: displayName,
    color = edit.color ?: color,
    caldavColor = edit.color?.argb ?: caldavColor,
    isVisible = edit.isVisible ?: isVisible,
)

/** Apple `calendar-color` format: `#RRGGBBAA`. */
private fun CalendarSourceColor.toCaldavHex(): String = argb.toCaldavHex()
