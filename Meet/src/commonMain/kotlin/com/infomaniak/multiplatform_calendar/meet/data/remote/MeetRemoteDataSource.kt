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
package com.infomaniak.multiplatform_calendar.meet.data.remote

import com.infomaniak.multiplatform_calendar.meet.data.remote.model.ApiMeetRoom
import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetErrorCause
import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.network.createHttpClient
import com.infomaniak.multiplatform_core.network.model.asSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess

private const val CALENDAR_API_HOST = "calendar.infomaniak.com"
private const val MEET_ROOM_ROUTE = "pim/calendar/meet-room"

internal fun createMeetHttpClient(): HttpClient = createHttpClient(host = CALENDAR_API_HOST, basePath = "api/")

internal class MeetRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getMeetRoomUrl(token: AccessToken): String {
        val response = httpClient.get(MEET_ROOM_ROUTE) { bearerAuth(token.value) }
        if (!response.status.isSuccess()) throw MeetException(MeetErrorCause.Api(response.status.value))
        return response.asSuccess<ApiMeetRoom>().meetRoomUrl
    }
}
