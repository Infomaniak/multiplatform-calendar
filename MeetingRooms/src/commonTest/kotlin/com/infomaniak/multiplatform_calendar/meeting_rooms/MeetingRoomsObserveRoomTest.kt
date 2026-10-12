package com.infomaniak.multiplatform_calendar.meeting_rooms

import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoom
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeetingRoomsObserveRoomTest : RobolectricTestsBase() {

    @Test
    fun missingRoomIsFetched() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success(resourceJson())) }

        val room = manager.observeRoom(ACCOUNT_ID, ROOM_UUID).filterNotNull().first()

        assertEquals("Salle American Diner", room.name)
        assertEquals("/pim/bookable-resources/$ROOM_UUID", requests.single().url.encodedPath)
        assertEquals(listOf("Salle American Diner"), manager.storedRoomNames())
    }

    @Test
    fun knownRoomIsNotFetched() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("[${resourceJson()}]")) }
        manager.sync(setOf(ACCOUNT_ID))

        val room = manager.observeRoom(ACCOUNT_ID, ROOM_UUID).filterNotNull().first()

        assertEquals("Salle American Diner", room.name)
        assertEquals(listOf("/pim/bookable-resources/all"), requests.map { it.url.encodedPath })
    }

    @Test
    fun forbiddenRoomStaysNull() = assertRoomStaysNull { respondJson(error("access_denied"), HttpStatusCode.Forbidden) }

    @Test
    fun unknownRoomStaysNull() = assertRoomStaysNull { respondJson(error("object_not_found"), HttpStatusCode.NotFound) }

    @Test
    fun resourceOtherThanMeetingRoomStaysNull() = assertRoomStaysNull {
        respondJson(success(resourceJson(category = "accessory")))
    }

    @Test
    fun fetchFailureStaysNull() = assertRoomStaysNull { throw IOException("offline") }

    @Test
    fun uuidIsEncodedInThePath() = runTest {
        val requested = CompletableDeferred<HttpRequestData>()
        val manager = testManager { requested.complete(it); respondJson(error("object_not_found"), HttpStatusCode.NotFound) }

        val job = launch { manager.observeRoom(ACCOUNT_ID, "../all").collect {} }
        val request = requested.await()
        job.cancel()

        assertEquals("/pim/bookable-resources/..%2Fall", request.url.encodedPath)
    }

    /** Collects the room until the server answered with [response]; a failure escaping the flow fails the test. */
    private fun assertRoomStaysNull(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = runTest {
        val answered = CompletableDeferred<Unit>()
        val manager = testManager { request ->
            try {
                response(request)
            } finally {
                answered.complete(Unit)
            }
        }
        val emissions = Channel<MeetingRoom?>(Channel.UNLIMITED)

        val job = launch { manager.observeRoom(ACCOUNT_ID, ROOM_UUID).collect(emissions::send) }
        assertNull(emissions.receive())
        answered.await()
        job.cancel()

        assertNull(emissions.tryReceive().getOrNull())
        assertEquals(emptyList(), manager.storedRoomNames())
    }

    private fun error(code: String) = """{ "result": "error", "error": { "code": "$code", "description": "" } }"""
}
