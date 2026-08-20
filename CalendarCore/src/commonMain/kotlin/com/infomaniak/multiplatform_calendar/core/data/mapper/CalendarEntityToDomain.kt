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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarSyncStateEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSyncStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSyncStatus.Failed
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSyncStatus.NeverSynced
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSyncStatus.Synced
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal fun CalendarEntity.toDomain() = Calendar(
    id = id,
    accountId = accountId,
    displayName = displayName,
    colors = CalendarColors.from(color),
    isVisible = isVisible,
    accessLevel = accessLevel,
    syncStatus = syncState.toSyncStatus(),
)

/** Derives the persisted status from the bookkeeping columns; the runtime-only `Syncing` is overlaid elsewhere. */
@OptIn(ExperimentalTime::class)
private fun CalendarSyncStateEntity.toSyncStatus(): CalendarSyncStatus {
    val lastSyncedAt = lastSyncedAtMs?.let(Instant::fromEpochMilliseconds)
    return when {
        lastSyncError != null -> Failed(lastSyncedAt, lastSyncError)
        lastSyncedAt != null -> Synced(lastSyncedAt)
        else -> NeverSynced
    }
}
