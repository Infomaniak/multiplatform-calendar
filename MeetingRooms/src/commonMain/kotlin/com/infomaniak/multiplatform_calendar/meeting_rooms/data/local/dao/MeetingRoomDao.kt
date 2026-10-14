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
package com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.MeetingRoomEntity
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomUuid
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.Flow
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Dao
public interface MeetingRoomDao {

    @Upsert
    public suspend fun upsertRooms(rooms: List<MeetingRoomEntity>)

    @Query("DELETE FROM meetingRooms WHERE accountId = :accountId")
    public suspend fun deleteRoomsForAccount(accountId: AccountId)

    @Transaction
    public suspend fun replaceRooms(accountId: AccountId, rooms: List<MeetingRoomEntity>) {
        deleteRoomsForAccount(accountId)
        upsertRooms(rooms)
    }

    @Query("SELECT * FROM meetingRooms WHERE accountId = :accountId ORDER BY name COLLATE NOCASE")
    public fun observeRooms(accountId: AccountId): Flow<List<MeetingRoomEntity>>

    @Query("SELECT * FROM meetingRooms WHERE accountId = :accountId AND uuid = :uuid")
    public fun observeRoom(accountId: AccountId, uuid: MeetingRoomUuid): Flow<MeetingRoomEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM meetingRooms WHERE accountId = :accountId AND uuid = :uuid)")
    public suspend fun hasRoom(accountId: AccountId, uuid: MeetingRoomUuid): Boolean
}
