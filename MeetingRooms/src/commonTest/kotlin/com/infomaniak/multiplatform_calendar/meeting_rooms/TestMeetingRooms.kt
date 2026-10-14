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

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.MeetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomUuid
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal val ACCOUNT_ID = AccountId(42L)
internal val OTHER_ACCOUNT_ID = AccountId(43L)

internal val ROOM_UUID = MeetingRoomUuid("9d3e6081-0000-0000-0000-000000000001")
internal val OTHER_ROOM_UUID = MeetingRoomUuid("9d3e6123-0000-0000-0000-000000000002")

internal fun resourceJson(
    uuid: MeetingRoomUuid = ROOM_UUID,
    name: String = "Salle American Diner",
    category: String = "meeting_room",
): String = """
    {
      "name": "$name",
      "color": "#FF0000",
      "uuid": "${uuid.value}",
      "category": "$category",
      "description": null,
      "location": "Rue Eugène-Marziano 25, 1227 Les Acacias",
      "floor": 3,
      "capacity": 30,
      "equipments": [{ "name": "screen", "category": "audio-video" }],
      "calendar_id": 12345,
      "account_id": 28
    }
""".trimIndent()

internal fun success(data: String): String = """{ "result": "success", "data": $data }"""

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun testHttpClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = HttpClient(MockEngine) {
    install(ContentNegotiation) { json(testJson()) }
    engine { addHandler(handler) }
}

@OptIn(ExperimentalSerializationApi::class)
private fun testJson(): Json = Json(DefaultJson) {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal suspend fun assertFailsWithCause(expected: MeetingRoomsErrorCause, block: suspend () -> Any?) {
    assertEquals(expected, assertFailsWith<MeetingRoomsException> { block() }.errorCause)
}

internal fun testDatabase(): MeetingRoomsDatabase =
    Room.inMemoryDatabaseBuilder<TestMeetingRoomsDatabase>().setDriver(BundledSQLiteDriver()).build()
