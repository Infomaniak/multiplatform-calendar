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
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseHexColor
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar

/**
 * Maps each remote calendar to a [CalendarEntity], preserving local-only fields (e.g.
 * [CalendarEntity.isVisible]) from [existingByCalendarId] when a matching row exists.
 *
 * Without this merge, an `@Upsert` of the mapped remote rows would reset the local prefs to their
 * entity defaults on every sync (`isVisible = true`, …).
 */
internal fun List<RemoteDavCalendar>.toEntitiesPreservingLocalPrefs(
    accountId: AccountId,
    existingByCalendarId: Map<CalendarId, CalendarEntity>,
): List<CalendarEntity> = map { remoteCalendar ->
    val freshEntity = remoteCalendar.toEntity(accountId)
    existingByCalendarId[freshEntity.id]
        ?.let { existing -> freshEntity.copy(isVisible = existing.isVisible) }
        ?: freshEntity
}

private fun RemoteDavCalendar.toEntity(accountId: AccountId) = CalendarEntity(
    id = CalendarId(url),
    accountId = accountId,
    displayName = displayName,
    color = parseHexColor(color)?.let(::CalendarColor),
    caldavColor = parseHexColor(color),
    ctag = ctag,
    accessLevel = accessLevel.toEntity(),
)
