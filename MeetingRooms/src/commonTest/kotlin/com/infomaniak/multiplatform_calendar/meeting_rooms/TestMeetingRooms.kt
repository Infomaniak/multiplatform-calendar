package com.infomaniak.multiplatform_calendar.meeting_rooms

import androidx.room3.useReaderConnection
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.MeetingRoomsDatabaseSource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.meetingRoomsDatabase
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal val ACCOUNT_ID = AccountId(42L)
internal val OTHER_ACCOUNT_ID = AccountId(43L)

internal const val ROOM_UUID = "9d3e6081-0000-0000-0000-000000000001"
internal const val OTHER_ROOM_UUID = "9d3e6123-0000-0000-0000-000000000002"

/** A bookable resource as the API returns it. */
internal fun resourceJson(
    uuid: String = ROOM_UUID,
    name: String = "Salle American Diner",
    category: String = "meeting_room",
): String = """
    {
      "name": "$name",
      "color": "#FF0000",
      "uuid": "$uuid",
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

@OptIn(ExperimentalSerializationApi::class)
private fun testJson(): Json = Json(DefaultJson) {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/** A manager whose server answers with [handler], with [ACCOUNT_ID] initialized when [initAccount]. */
internal suspend fun testManager(
    initAccount: Boolean = true,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): MeetingRoomsManager = MeetingRoomsManager(
    database = meetingRoomsDatabase(MeetingRoomsDatabaseSource.InMemory, driver = TestDatabaseFactory.driver()),
    httpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(testJson()) }
        engine { addHandler(handler) }
    },
).apply { if (initAccount) initAccount(ACCOUNT_ID, AccessToken("token")) }

/** Names of the rooms stored for [accountId], sorted. */
internal suspend fun MeetingRoomsManager.storedRoomNames(accountId: AccountId = ACCOUNT_ID): List<String> =
    database.useReaderConnection { connection ->
        connection.usePrepared("SELECT name FROM meetingRooms WHERE accountId = ? ORDER BY name") { statement ->
            statement.bindLong(1, accountId.value)
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
    }

internal suspend fun assertFailsWithCause(expected: MeetingRoomsErrorCause, block: suspend () -> Unit) {
    assertEquals(expected, assertFailsWith<MeetingRoomsException> { block() }.errorCause)
}
