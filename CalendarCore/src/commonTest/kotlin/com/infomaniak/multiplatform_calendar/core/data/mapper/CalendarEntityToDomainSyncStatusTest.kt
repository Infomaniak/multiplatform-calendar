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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarSyncStateEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSyncStatus.Failed
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSyncStatus.NeverSynced
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSyncStatus.Synced
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.SyncErrorReason
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.SyncErrorReason.NETWORK
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.SyncErrorReason.UNKNOWN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CalendarEntityToDomainSyncStatusTest {

    @Test
    fun noSyncBookkeeping_mapsToNeverSynced() {
        val entity = calendarEntity()

        assertEquals(NeverSynced, entity.toDomain().syncStatus)
    }

    @Test
    fun successfulSyncTimestamp_mapsToSyncedWithThatInstant() {
        val syncedAt = Instant.parse("2026-06-15T14:00:00Z")
        val entity = calendarEntity(lastSyncedAtMs = syncedAt.toEpochMilliseconds())

        assertEquals(Synced(syncedAt), entity.toDomain().syncStatus)
    }

    @Test
    fun lastSyncError_mapsToFailed_keepingPreviousSyncedInstant() {
        val syncedAt = Instant.parse("2026-06-15T14:00:00Z")
        val entity = calendarEntity(
            lastSyncedAtMs = syncedAt.toEpochMilliseconds(),
            lastSyncError = NETWORK,
        )

        assertEquals(
            Failed(lastSyncedAt = syncedAt, reason = NETWORK),
            entity.toDomain().syncStatus,
        )
    }

    @Test
    fun failureWithoutPreviousSuccess_mapsToFailedWithNullInstant() {
        val entity = calendarEntity(lastSyncError = UNKNOWN)

        assertEquals(
            Failed(lastSyncedAt = null, reason = UNKNOWN),
            entity.toDomain().syncStatus,
        )
    }

    private fun calendarEntity(
        lastSyncedAtMs: Long? = null,
        lastSyncError: SyncErrorReason? = null,
    ) = CalendarEntity(
        id = CalendarId("https://dav.example/cal/1/"),
        accountId = AccountId(1),
        displayName = "Cal",
        color = null,
        syncState = CalendarSyncStateEntity(
            lastSyncedAtMs = lastSyncedAtMs,
            lastSyncError = lastSyncError,
        ),
    )
}
