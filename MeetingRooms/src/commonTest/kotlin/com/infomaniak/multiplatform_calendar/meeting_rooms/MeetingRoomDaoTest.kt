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
package com.infomaniak.multiplatform_calendar.meeting_rooms

import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.MeetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.EquipmentEntity
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.MeetingRoomEntity
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomColor
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomUuid
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.database.testing.RobolectricTestsBase
import com.infomaniak.multiplatform_core.database.testing.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetingRoomDaoTest : RobolectricTestsBase() {

    private val dao = testDatabase<MeetingRoomsDatabase>().meetingRoomDao()

    @Test
    fun roomsAreObservedByAccountSortedByName() = runTest {
        dao.upsertRooms(
            listOf(
                room(uuid = ROOM_UUID, name = "salle B"),
                room(uuid = OTHER_ROOM_UUID, name = "Salle A"),
                room(uuid = ROOM_UUID, name = "Other account", accountId = OTHER_ACCOUNT_ID),
            ),
        )

        assertEquals(listOf("Salle A", "salle B"), dao.observeRooms(ACCOUNT_ID).first().map { it.name })
    }

    @Test
    fun roomIsObservedWithItsEquipments() = runTest {
        val room = room(equipments = listOf(EquipmentEntity(name = "screen", category = "audio-video")))
        assertNull(dao.observeRoom(ACCOUNT_ID, ROOM_UUID).first())

        dao.upsertRooms(listOf(room))

        assertEquals(room, dao.observeRoom(ACCOUNT_ID, ROOM_UUID).first())
    }

    @Test
    fun upsertReplacesTheRoom() = runTest {
        dao.upsertRooms(listOf(room(name = "Before")))
        dao.upsertRooms(listOf(room(name = "After")))

        assertEquals(listOf("After"), dao.observeRooms(ACCOUNT_ID).first().map { it.name })
    }

    @Test
    fun hasRoomIsScopedToTheAccount() = runTest {
        dao.upsertRooms(listOf(room()))

        assertTrue(dao.hasRoom(ACCOUNT_ID, ROOM_UUID))
        assertFalse(dao.hasRoom(OTHER_ACCOUNT_ID, ROOM_UUID))
    }

    @Test
    fun deletingTheRoomsOfAnAccountKeepsTheOthers() = runTest {
        dao.upsertRooms(listOf(room(), room(accountId = OTHER_ACCOUNT_ID)))

        dao.deleteRoomsForAccount(ACCOUNT_ID)

        assertEquals(emptyList(), dao.observeRooms(ACCOUNT_ID).first())
        assertEquals(1, dao.observeRooms(OTHER_ACCOUNT_ID).first().size)
    }

    private fun room(
        uuid: MeetingRoomUuid = ROOM_UUID,
        name: String = "Salle American Diner",
        accountId: AccountId = ACCOUNT_ID,
        equipments: List<EquipmentEntity> = emptyList(),
    ) = MeetingRoomEntity(
        accountId = accountId,
        uuid = uuid,
        name = name,
        color = MeetingRoomColor(0xFFFF0000.toInt()),
        description = null,
        location = null,
        floor = 3,
        capacity = 30,
        equipments = equipments,
    )
}
