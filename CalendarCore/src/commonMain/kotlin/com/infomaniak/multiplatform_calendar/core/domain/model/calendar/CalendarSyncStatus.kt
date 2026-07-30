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
package com.infomaniak.multiplatform_calendar.core.domain.model.calendar

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Per-calendar sync state exposed to consumers (UI badge, "synced X ago", error banner). [Syncing]
 * is runtime-only; the others are derived from persisted bookkeeping. [Failed] keeps the last
 * successful [lastSyncedAt] (or `null`) so the UI can still show it alongside the error.
 */
@OptIn(ExperimentalTime::class)
public sealed interface CalendarSyncStatus {

    public data object NeverSynced : CalendarSyncStatus

    public data object Syncing : CalendarSyncStatus

    public data class Synced(val lastSyncedAt: Instant) : CalendarSyncStatus

    public data class Failed(
        val lastSyncedAt: Instant?,
        val reason: SyncErrorReason,
    ) : CalendarSyncStatus
}
