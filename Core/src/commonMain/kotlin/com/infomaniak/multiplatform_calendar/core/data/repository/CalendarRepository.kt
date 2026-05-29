/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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

package com.infomaniak.multiplatform_calendar.core.data.repository

import com.infomaniak.multiplatform_calendar.core.data.remote.CaldavClient
import com.infomaniak.multiplatform_calendar.core.data.remote.model.CaldavCredentials
import com.infomaniak.multiplatform_calendar.core.data.remote.model.RemoteCalendar
import com.infomaniak.multiplatform_calendar.core.data.remote.model.RemoteEvent

class CalendarRepository(
    private val caldavClient: CaldavClient,
) {

    suspend fun discoverCalendars(
        baseUrl: String,
        username: String,
        password: String,
    ): List<RemoteCalendar> {
        val credentials = CaldavCredentials(baseUrl, username, password)
        return caldavClient.discoverCalendars(credentials)
    }

    suspend fun syncCalendar(
        baseUrl: String,
        username: String,
        password: String,
        calendarUrl: String,
    ): List<RemoteEvent> {
        val credentials = CaldavCredentials(baseUrl, username, password)
        return caldavClient.getEvents(credentials, calendarUrl)
    }
}

