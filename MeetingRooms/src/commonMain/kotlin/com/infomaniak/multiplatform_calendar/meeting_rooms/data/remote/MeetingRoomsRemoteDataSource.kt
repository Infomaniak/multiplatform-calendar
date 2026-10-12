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
package com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote

import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiAvailabilityRequest
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBookableResource
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBusyPeriod
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBusyPeriodsRequest
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.network.createHttpClient
import com.infomaniak.multiplatform_core.network.model.asSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

internal const val CALENDAR_API_HOST = "calendar.infomaniak.com"

internal fun createMeetingRoomsHttpClient(): HttpClient = createHttpClient(host = CALENDAR_API_HOST, basePath = "api/")

internal class MeetingRoomsRemoteDataSource(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Every resource the user can book, across all the organizations of the account. */
    suspend fun getBookableResources(token: AccessToken): List<ApiBookableResource> =
        httpClient.get(MeetingRoomsRoutes.ALL) { bearerAuth(token.value) }.successOrThrow().asSuccess()

    /** Null when the resource does not exist or the user cannot book it. */
    suspend fun getBookableResource(token: AccessToken, uuid: String): ApiBookableResource? {
        val response = httpClient.get(MeetingRoomsRoutes.resource(uuid)) { bearerAuth(token.value) }
        return when (response.status) {
            HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> null
            else -> response.successOrThrow().asSuccess()
        }
    }

    /** Availability by resource uuid, restricted to [uuid] when given. */
    suspend fun getAvailability(token: AccessToken, request: ApiAvailabilityRequest, uuid: String?): Map<String, Boolean> {
        val route = uuid?.let(MeetingRoomsRoutes::availability) ?: MeetingRoomsRoutes.AVAILABILITY
        return httpClient.post(route) { jsonBody(token, request) }
            .successOrThrow()
            .asSuccess<JsonElement>()
            .asObject()
            .mapValues { (_, available) -> available.jsonPrimitive.boolean }
    }

    /** Bookings by resource uuid. */
    suspend fun getBusyPeriods(token: AccessToken, request: ApiBusyPeriodsRequest): Map<String, List<ApiBusyPeriod>> {
        return httpClient.post(MeetingRoomsRoutes.BUSY_PERIODS) { jsonBody(token, request) }
            .successOrThrow()
            .asSuccess<JsonElement>()
            .asObject()
            .entries
            .associate { (key, periods) ->
                key.removePrefix(BUSY_PERIODS_KEY_PREFIX) to
                    json.decodeFromJsonElement(ListSerializer(ApiBusyPeriod.serializer()), periods)
            }
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(token: AccessToken, body: T) {
        bearerAuth(token.value)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpResponse.successOrThrow(): HttpResponse = apply {
        if (!status.isSuccess()) throw MeetingRoomsException(MeetingRoomsErrorCause.Api(status.value))
    }

    /** PHP encodes an empty map as an empty array. */
    private fun JsonElement.asObject(): Map<String, JsonElement> = this as? JsonObject ?: emptyMap()

    private companion object {
        const val BUSY_PERIODS_KEY_PREFIX = "bookable#"
    }
}

private object MeetingRoomsRoutes {
    const val ALL = "pim/bookable-resources/all"
    const val AVAILABILITY = "pim/bookable-resources/availability"
    const val BUSY_PERIODS = "pim/event/bookable-availability"

    fun resource(uuid: String) = "pim/bookable-resources/${uuid.encodeURLPathPart()}"
    fun availability(uuid: String) = "${resource(uuid)}/availability"
}
