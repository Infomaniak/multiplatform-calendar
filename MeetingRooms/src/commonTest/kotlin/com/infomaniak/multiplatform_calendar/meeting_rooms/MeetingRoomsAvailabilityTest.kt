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

import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class MeetingRoomsAvailabilityTest : RobolectricTestsBase() {

    private val start = Instant.parse("2026-10-12T07:00:00Z")
    private val end = Instant.parse("2026-10-12T08:30:00Z")
    private val zurich = TimeZone.of("Europe/Zurich")

    @Test
    fun availabilitySendsLocalDatesWithTheirTimeZone() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager {
            requests.add(it)
            respondJson(success("""{ "${ROOM_UUID.value}": true, "${OTHER_ROOM_UUID.value}": false }"""))
        }

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
        val manager = testManager { respondJson(success("{}")) }

        assertEquals(emptyMap(), manager.availability(ACCOUNT_ID, start, end, zurich))
    }

    @Test
    fun isAvailableAsksForTheSingleRoom() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("""{ "${ROOM_UUID.value}": true }""")) }

        assertTrue(manager.isAvailable(ACCOUNT_ID, ROOM_UUID, start, end, zurich))
        assertEquals("/pim/bookable-resources/${ROOM_UUID.value}/availability", requests.single().url.encodedPath)
    }

    @Test
    fun isAvailableIsFalseWhenTheRoomIsMissingFromTheAnswer() = runTest {
        val manager = testManager { respondJson(success("{}")) }

        assertFalse(manager.isAvailable(ACCOUNT_ID, ROOM_UUID, start, end, zurich))
    }

    @Test
    fun availabilityThrowsApiErrorOnFailure() = runTest {
        val manager = testManager { respondJson("", HttpStatusCode.InternalServerError) }

        assertFailsWithCause(MeetingRoomsErrorCause.Api(500)) { manager.availability(ACCOUNT_ID, start, end, zurich) }
    }

    @Test
    fun availabilityWithoutInitAccountThrows() = runTest {
        val manager = testManager(initAccount = false) { respondJson(success("{}")) }

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
                      "bookable#${ROOM_UUID.value}": [
                        {
                          "id": 101,
                          "parent_event_id": 100,
                          "calendarId": 12345,
                          "title": "Occupé",
                          "start": "2026-10-12T09:00:00+02:00",
                          "end": "2026-10-12T10:00:00+02:00",
                          "full_day": false,
                          "fullday": true,
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
            isFullDay = true,
            title = "Occupé",
            organizer = "user@infomaniak.com",
            isOwner = true,
        )
        assertEquals(mapOf(ROOM_UUID to listOf(expected)), busyPeriods)
        assertEquals("/pim/event/bookable-availability", requests.single().url.encodedPath)
        val expectedBody = """
            { "bookable_resource_uuids": ["${ROOM_UUID.value}"], "from": "2026-10-12 07:00:00", "to": "2026-10-12 08:30:00" }
        """
        assertEquals(Json.parseToJsonElement(expectedBody), requests.single().jsonBody())
    }

    @Test
    fun busyPeriodsRejectAMalformedAnswer() = runTest {
        val manager = testManager { respondJson(success("[1]")) }

        val exception = assertFailsWith<MeetingRoomsException> {
            manager.busyPeriods(ACCOUNT_ID, setOf(ROOM_UUID), start, end)
        }
        assertIs<MeetingRoomsErrorCause.Unknown>(exception.errorCause)
    }

    @Test
    fun busyPeriodsWithoutBookingsAreEmpty() = runTest {
        val manager = testManager { respondJson(success("{}")) }

        assertEquals(emptyMap(), manager.busyPeriods(ACCOUNT_ID, setOf(ROOM_UUID), start, end))
    }

    @Test
    fun busyPeriodsOfNoRoomDoNotHitTheNetwork() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("{}")) }

        assertEquals(emptyMap(), manager.busyPeriods(ACCOUNT_ID, emptySet(), start, end))
        assertEquals(emptyList(), requests)
    }

    private suspend fun HttpRequestData.jsonBody(): JsonElement = Json.parseToJsonElement(body.toByteArray().decodeToString())
}
