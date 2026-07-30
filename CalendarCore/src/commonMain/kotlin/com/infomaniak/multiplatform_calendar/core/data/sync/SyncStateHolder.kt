/*
 * Infomaniak Calendar - Multiplatform
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
package com.infomaniak.multiplatform_calendar.core.data.sync

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runtime-only registry of the calendars currently syncing, overlaid as `Syncing` on top of the
 * persisted status when reading calendars. Assumes syncs are not concurrent for the same calendar
 * (marked/cleared once per run); if that ever changes, switch the `Set` to reference counting.
 */
@SingleIn(AppScope::class)
@Inject
internal class SyncStateHolder {

    private val _syncingCalendarIds = MutableStateFlow<Set<CalendarId>>(emptySet())
    val syncingCalendarIds: StateFlow<Set<CalendarId>> = _syncingCalendarIds.asStateFlow()

    fun markSyncing(calendarId: CalendarId) {
        _syncingCalendarIds.update { it + calendarId }
    }

    fun clear(calendarId: CalendarId) {
        _syncingCalendarIds.update { it - calendarId }
    }
}
