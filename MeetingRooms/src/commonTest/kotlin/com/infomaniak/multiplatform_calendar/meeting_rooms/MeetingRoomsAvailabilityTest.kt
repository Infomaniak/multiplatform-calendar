package com.infomaniak.multiplatform_calendar.meeting_rooms

import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MeetingRoomsAvailabilityTest : RobolectricTestsBase() {

    private val start = Instant.parse("2026-10-12T07:00:00Z")
    private val end = Instant.parse("2026-10-12T08:30:00Z")
    private val zurich = TimeZone.of("Europe/Zurich")

    @Test
    fun availabilitySendsLocalDatesWithTheirTimeZone() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("""{ "$ROOM_UUID": true, "$OTHER_ROOM_UUID": false }""")) }

        val availability = manager.availability(ACCOUNT_ID, start, end, zurich, rrule = "FREQ=DAILY;COUNT=2")

        assertEquals(mapOf(ROOM_UUID to true, OTHER_ROOM_UUID to false), availability)
        assertEquals("/pim/bookable-resources/availability", requests.single().url.encodedPath)
        val expectedBody = """
            {
              "start": "2026-10-12 09:00:00",
              "end": "2026-10-12 10:30:00",
              "timezone_start": "Europe/Zurich",
              "timezone_end": "Europe/Zurich",
              "rrule": "FREQ=DAILY;COUNT=2"
            }
        """
        assertEquals(Json.parseToJsonElement(expectedBody), requests.single().jsonBody())
    }

    @Test
    fun availabilityWithoutResourcesIsEmpty() = runTest {
        val manager = testManager { respondJson(success("[]")) }

        assertEquals(emptyMap(), manager.availability(ACCOUNT_ID, start, end, zurich))
    }

    @Test
    fun isAvailableAsksForTheSingleRoom() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("""{ "$ROOM_UUID": true }""")) }

        assertTrue(manager.isAvailable(ACCOUNT_ID, ROOM_UUID, start, end, zurich))
        assertEquals("/pim/bookable-resources/$ROOM_UUID/availability", requests.single().url.encodedPath)
    }

    @Test
    fun isAvailableIsFalseWhenTheRoomIsMissingFromTheAnswer() = runTest {
        val manager = testManager { respondJson(success("[]")) }

        assertFalse(manager.isAvailable(ACCOUNT_ID, ROOM_UUID, start, end, zurich))
    }

    @Test
    fun availabilityThrowsApiErrorOnFailure() = runTest {
        val manager = testManager { respondJson("", HttpStatusCode.InternalServerError) }

        assertFailsWithCause(MeetingRoomsErrorCause.Api(500)) { manager.availability(ACCOUNT_ID, start, end, zurich) }
    }

    @Test
    fun availabilityWithoutInitAccountThrows() = runTest {
        val manager = testManager(initAccount = false) { respondJson(success("[]")) }

        assertFailsWithCause(MeetingRoomsErrorCause.AccountNotInitialized(ACCOUNT_ID)) {
            manager.availability(ACCOUNT_ID, start, end, zurich)
        }
    }

    @Test
    fun busyPeriodsAreGroupedByRoomUuid() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager {
            requests.add(it)
            respondJson(
                success(
                    """
                    {
                      "bookable#$ROOM_UUID": [
                        {
                          "id": 101,
                          "parent_event_id": 100,
                          "calendarId": 12345,
                          "title": "Occupé",
                          "start": "2026-10-12T09:00:00+02:00",
                          "end": "2026-10-12T10:00:00+02:00",
                          "full_day": false,
                          "fullday": false,
                          "private": false,
                          "organizer": "user@infomaniak.com",
                          "is_owner": true
                        }
                      ]
                    }
                    """,
                ),
            )
        }

        val busyPeriods = manager.busyPeriods(ACCOUNT_ID, setOf(ROOM_UUID), start, end)

        val expected = BusyPeriod(
            start = Instant.parse("2026-10-12T07:00:00Z"),
            end = Instant.parse("2026-10-12T08:00:00Z"),
            isFullDay = false,
            title = "Occupé",
            organizer = "user@infomaniak.com",
            isOwner = true,
        )
        assertEquals(mapOf(ROOM_UUID to listOf(expected)), busyPeriods)
        assertEquals("/pim/event/bookable-availability", requests.single().url.encodedPath)
        val expectedBody = """
            { "bookable_resource_uuids": ["$ROOM_UUID"], "from": "2026-10-12 07:00:00", "to": "2026-10-12 08:30:00" }
        """
        assertEquals(Json.parseToJsonElement(expectedBody), requests.single().jsonBody())
    }

    @Test
    fun busyPeriodsWithoutBookingsAreEmpty() = runTest {
        val manager = testManager { respondJson(success("[]")) }

        assertEquals(emptyMap(), manager.busyPeriods(ACCOUNT_ID, setOf(ROOM_UUID), start, end))
    }

    @Test
    fun busyPeriodsOfNoRoomDoNotHitTheNetwork() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("[]")) }

        assertEquals(emptyMap(), manager.busyPeriods(ACCOUNT_ID, emptySet(), start, end))
        assertEquals(emptyList(), requests)
    }

    private suspend fun HttpRequestData.jsonBody(): JsonElement = Json.parseToJsonElement(body.toByteArray().decodeToString())
}
