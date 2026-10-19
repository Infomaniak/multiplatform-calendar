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

import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.Equipment
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.MeetingRoom
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.MeetingRoomColor
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingRoomsSyncTest : RobolectricTestsBase() {

    @Test
    fun initAccountDoesNotSync() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val _ = testManager { requests.add(it); respondJson(success("[]")) }

        assertEquals(emptyList(), requests)
    }

    @Test
    fun syncRequestsEveryBookableResourceWithTheToken() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("[]")) }

        manager.sync(setOf(ACCOUNT_ID))

        assertEquals("/pim/bookable-resources/all", requests.single().url.encodedPath)
        assertEquals("Bearer token", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun syncKeepsOnlyMeetingRooms() = runTest {
        val resources = listOf(
            resourceJson(uuid = ROOM_UUID, name = "Salle Chat"),
            resourceJson(uuid = OTHER_ROOM_UUID, name = "Beamer", category = "accessory"),
        )
        val manager = testManager { respondJson(success(resources.joinToString(prefix = "[", postfix = "]"))) }

        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(listOf("Salle Chat"), manager.storedRoomNames())
    }

    @Test
    fun observeRoomsMapsEveryField() = runTest {
        val manager = testManager { respondJson(success("[${resourceJson()}]")) }

        manager.sync(setOf(ACCOUNT_ID))

        val expected = MeetingRoom(
            uuid = ROOM_UUID,
            name = "Salle American Diner",
            color = MeetingRoomColor(0xFFFF0000.toInt()),
            description = null,
            location = "Rue Eugène-Marziano 25, 1227 Les Acacias",
            floor = 3,
            capacity = 30,
            equipments = listOf(Equipment(name = "screen", category = "audio-video")),
        )
        assertEquals(listOf(expected), manager.observeRooms(ACCOUNT_ID).first())
    }

    @Test
    fun observeRoomsSortsByName() = runTest {
        val resources = listOf(resourceJson(uuid = ROOM_UUID, name = "salle b"), resourceJson(uuid = OTHER_ROOM_UUID, name = "Salle A"))
        val manager = testManager { respondJson(success(resources.joinToString(prefix = "[", postfix = "]"))) }

        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(listOf("Salle A", "salle b"), manager.observeRooms(ACCOUNT_ID).first().map { it.name })
    }

    @Test
    fun syncReplacesRooms() = runTest {
        var callCount = 0
        val manager = testManager {
            val name = if (callCount++ == 0) "Salle Chat" else "Salle Chien"
            respondJson(success("[${resourceJson(name = name)}]"))
        }

        manager.sync(setOf(ACCOUNT_ID))
        assertEquals(listOf("Salle Chat"), manager.storedRoomNames())
        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(listOf("Salle Chien"), manager.storedRoomNames())
    }

    @Test
    fun syncWithoutAccountsSyncsEveryInitializedAccount() = runTest {
        val manager = testManager { respondJson(success("[${resourceJson()}]")) }
        manager.initAccount(OTHER_ACCOUNT_ID, AccessToken("other"))

        manager.sync()

        assertEquals(listOf("Salle American Diner"), manager.storedRoomNames(ACCOUNT_ID))
        assertEquals(listOf("Salle American Diner"), manager.storedRoomNames(OTHER_ACCOUNT_ID))
    }

    @Test
    fun failingAccountDoesNotPreventTheOthers() = runTest {
        val manager = testManager { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer other") {
                respondJson("", HttpStatusCode.InternalServerError)
            } else {
                respondJson(success("[${resourceJson()}]"))
            }
        }
        manager.initAccount(OTHER_ACCOUNT_ID, AccessToken("other"))

        assertFailsWithCause(MeetingRoomsErrorCause.Api(500)) { manager.sync() }

        assertEquals(listOf("Salle American Diner"), manager.storedRoomNames(ACCOUNT_ID))
    }

    @Test
    fun removeAccountClearsRoomsAndToken() = runTest {
        val manager = testManager { respondJson(success("[${resourceJson()}]")) }
        manager.sync(setOf(ACCOUNT_ID))

        manager.removeAccount(ACCOUNT_ID)

        assertEquals(emptyList(), manager.storedRoomNames())
        assertFailsWithCause(MeetingRoomsErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun syncWithoutInitAccountThrows() = runTest {
        val manager = testManager(initAccount = false) { respondJson(success("[]")) }

        assertFailsWithCause(MeetingRoomsErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun unexpectedStatusThrowsApiErrorAndKeepsRooms() = runTest {
        var callCount = 0
        val manager = testManager {
            if (callCount++ == 0) respondJson(success("[${resourceJson()}]")) else respondJson("", HttpStatusCode.InternalServerError)
        }
        manager.sync(setOf(ACCOUNT_ID))

        assertFailsWithCause(MeetingRoomsErrorCause.Api(500)) { manager.sync(setOf(ACCOUNT_ID)) }

        assertEquals(listOf("Salle American Diner"), manager.storedRoomNames())
    }

    @Test
    fun unreachableServerThrowsNetworkError() = runTest {
        val manager = testManager { throw IOException("offline") }

        assertFailsWithCause(MeetingRoomsErrorCause.Network) { manager.sync(setOf(ACCOUNT_ID)) }
    }
}
