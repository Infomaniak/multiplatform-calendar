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
package com.infomaniak.multiplatform_calendar.bookable_resources

import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.BookableResourceEntity
import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.EquipmentEntity
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceColor
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookableResourceDaoTest : RobolectricTestsBase() {

    private val dao = testDatabase().bookableResourceDao()

    @Test
    fun resourcesAreObservedByAccountSortedByName() = runTest {
        dao.upsertResources(
            listOf(
                resource(uuid = RESOURCE_UUID, name = "salle B"),
                resource(uuid = OTHER_RESOURCE_UUID, name = "Salle A"),
                resource(uuid = RESOURCE_UUID, name = "Other account", accountId = OTHER_ACCOUNT_ID),
            ),
        )

        assertEquals(listOf("Salle A", "salle B"), dao.observeResources(ACCOUNT_ID).first().map { it.name })
    }

    @Test
    fun resourceIsObservedWithItsEquipments() = runTest {
        val resource = resource(equipments = listOf(EquipmentEntity(name = "screen", category = "audio-video")))
        assertNull(dao.observeResource(ACCOUNT_ID, RESOURCE_UUID).first())

        dao.upsertResources(listOf(resource))

        assertEquals(resource, dao.observeResource(ACCOUNT_ID, RESOURCE_UUID).first())
    }

    @Test
    fun upsertReplacesTheResource() = runTest {
        dao.upsertResources(listOf(resource(name = "Before")))
        dao.upsertResources(listOf(resource(name = "After")))

        assertEquals(listOf("After"), dao.observeResources(ACCOUNT_ID).first().map { it.name })
    }

    @Test
    fun hasResourceIsScopedToTheAccount() = runTest {
        dao.upsertResources(listOf(resource()))

        assertTrue(dao.hasResource(ACCOUNT_ID, RESOURCE_UUID))
        assertFalse(dao.hasResource(OTHER_ACCOUNT_ID, RESOURCE_UUID))
    }

    @Test
    fun deletingTheResourcesOfAnAccountKeepsTheOthers() = runTest {
        dao.upsertResources(listOf(resource(), resource(accountId = OTHER_ACCOUNT_ID)))

        dao.deleteResourcesForAccount(ACCOUNT_ID)

        assertEquals(emptyList(), dao.observeResources(ACCOUNT_ID).first())
        assertEquals(1, dao.observeResources(OTHER_ACCOUNT_ID).first().size)
    }

    private fun resource(
        uuid: BookableResourceUuid = RESOURCE_UUID,
        name: String = "Salle American Diner",
        accountId: AccountId = ACCOUNT_ID,
        equipments: List<EquipmentEntity> = emptyList(),
    ) = BookableResourceEntity(
        accountId = accountId,
        uuid = uuid,
        name = name,
        color = BookableResourceColor(0xFFFF0000.toInt()),
        description = null,
        location = null,
        floor = 3,
        capacity = 30,
        equipments = equipments,
    )
}
