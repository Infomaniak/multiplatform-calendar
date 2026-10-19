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

import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.BookableResourcesRemoteDataSource
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiBookableResource
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiEquipment
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesErrorCause
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookableResourcesRemoteDataSourceTest {

    private val token = AccessToken("token")
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun bookableResourcesAreDecoded() = runTest {
        val dataSource = dataSource { respondJson(success("[${resourceJson()}]")) }

        assertEquals(listOf(expectedResource), dataSource.getBookableResources(token))
        assertEquals("/pim/bookable-resources/all", requests.single().url.encodedPath)
        assertEquals("Bearer token", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun bookableResourceIsDecoded() = runTest {
        val dataSource = dataSource { respondJson(success(resourceJson())) }

        assertEquals(expectedResource, dataSource.getBookableResource(token, ROOM_UUID))
        assertEquals("/pim/bookable-resources/${ROOM_UUID.value}", requests.single().url.encodedPath)
    }

    @Test
    fun forbiddenResourceIsNull() = runTest {
        val dataSource = dataSource { respondJson(error("access_denied"), HttpStatusCode.Forbidden) }

        assertNull(dataSource.getBookableResource(token, ROOM_UUID))
    }

    @Test
    fun unknownResourceIsNull() = runTest {
        val dataSource = dataSource { respondJson(error("object_not_found"), HttpStatusCode.NotFound) }

        assertNull(dataSource.getBookableResource(token, ROOM_UUID))
    }

    @Test
    fun serverErrorThrowsApiError() = runTest {
        val dataSource = dataSource { respondJson("", HttpStatusCode.InternalServerError) }

        assertFailsWithCause(BookableResourcesErrorCause.Api(500)) { dataSource.getBookableResources(token) }
    }

    private fun dataSource(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = BookableResourcesRemoteDataSource(testHttpClient { requests.add(it); response(it) })

    private fun error(code: String) = """{ "result": "error", "error": { "code": "$code", "description": "" } }"""

    private val expectedResource = ApiBookableResource(
        uuid = ROOM_UUID.value,
        name = "Salle American Diner",
        color = "#FF0000",
        category = "meeting_room",
        location = "Rue Eugène-Marziano 25, 1227 Les Acacias",
        floor = 3,
        capacity = 30,
        equipments = listOf(ApiEquipment(name = "screen", category = "audio-video")),
    )
}
