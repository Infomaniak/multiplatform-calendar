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

import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.ApiBookableResource
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomUuid
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsErrorCause
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.exceptions.MeetingRoomsException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.network.createHttpClient
import com.infomaniak.multiplatform_core.network.model.asSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

internal const val CALENDAR_API_HOST = "calendar.infomaniak.com"

internal fun createMeetingRoomsHttpClient(): HttpClient = createHttpClient(host = CALENDAR_API_HOST, basePath = "api/")

internal class MeetingRoomsRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getBookableResources(token: AccessToken): List<ApiBookableResource> =
        httpClient.get(MeetingRoomsRoutes.ALL) { bearerAuth(token.value) }.successOrThrow().asSuccess()

    suspend fun getBookableResource(token: AccessToken, uuid: MeetingRoomUuid): ApiBookableResource? {
        val response = httpClient.get(MeetingRoomsRoutes.resource(uuid)) { bearerAuth(token.value) }
        return when (response.status) {
            HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> null
            else -> response.successOrThrow().asSuccess()
        }
    }

    private fun HttpResponse.successOrThrow(): HttpResponse = apply {
        if (!status.isSuccess()) throw MeetingRoomsException(MeetingRoomsErrorCause.Api(status.value))
    }
}
