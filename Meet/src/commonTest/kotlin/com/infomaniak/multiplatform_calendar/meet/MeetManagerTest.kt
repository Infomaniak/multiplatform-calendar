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
package com.infomaniak.multiplatform_calendar.meet

import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetErrorCause
import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeetManagerTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun generatedRoomUrlIsDecoded() = runTest {
        val manager = manager { respondJson("""{ "result": "success", "data": { "meet_room_url": "$ROOM_URL" } }""") }

        assertEquals(ROOM_URL, manager.generateRoomUrl(ACCOUNT_ID))
        assertEquals("/pim/calendar/meet-room", requests.single().url.encodedPath)
        assertEquals("Bearer $TOKEN", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun uninitializedAccountFails() = runTest {
        val manager = manager(initAccount = false) { error("No request expected") }

        assertFailsWithCause(MeetErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.generateRoomUrl(ACCOUNT_ID) }
        assertEquals(emptyList(), requests)
    }

    @Test
    fun removedAccountFails() = runTest {
        val manager = manager { error("No request expected") }
        manager.removeAccount(ACCOUNT_ID)

        assertFailsWithCause(MeetErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.generateRoomUrl(ACCOUNT_ID) }
    }

    @Test
    fun httpErrorThrowsApiError() = runTest {
        val manager = manager { respondJson("", HttpStatusCode.Forbidden) }

        assertFailsWithCause(MeetErrorCause.Api(403)) { manager.generateRoomUrl(ACCOUNT_ID) }
    }

    @Test
    fun ioErrorThrowsNetworkError() = runTest {
        val manager = manager { throw IOException("offline") }

        assertFailsWithCause(MeetErrorCause.Network) { manager.generateRoomUrl(ACCOUNT_ID) }
    }

    private suspend fun manager(
        initAccount: Boolean = true,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): MeetManager {
        val httpClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(testJson()) }
            engine { addHandler { request -> requests.add(request); handler(request) } }
        }
        return MeetManager(httpClient).apply { if (initAccount) initAccount(ACCOUNT_ID, AccessToken(TOKEN)) }
    }

    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @OptIn(ExperimentalSerializationApi::class)
    private fun testJson(): Json = Json(DefaultJson) {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private suspend fun assertFailsWithCause(expected: MeetErrorCause, block: suspend () -> Any?) {
        assertEquals(expected, assertFailsWith<MeetException> { block() }.errorCause)
    }

    private companion object {
        val ACCOUNT_ID = AccountId(42L)
        const val TOKEN = "token"
        const val ROOM_URL = "https://kmeet.infomaniak.com/abcd-efgh-ijkl-mnop"
    }
}
