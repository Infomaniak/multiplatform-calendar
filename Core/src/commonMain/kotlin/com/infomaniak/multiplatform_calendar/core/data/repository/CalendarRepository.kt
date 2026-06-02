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

import com.infomaniak.multiplatform_calendar.caldav.data.remote.CaldavClient
import com.infomaniak.multiplatform_calendar.caldav.data.remote.model.CaldavCredentials
import com.infomaniak.multiplatform_calendar.caldav.data.remote.model.RemoteCalendar
import com.infomaniak.multiplatform_calendar.core.data.local.dao.CalendarDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class CalendarRepository(
    private val caldavClient: CaldavClient,
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
) {

    fun observeCalendars(accountId: AccountId): Flow<List<Calendar>> {
        return calendarDao.observeByAccountId(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun syncCalendars(
        accountId: AccountId,
        credentials: CaldavCredentials,
    ) {
        val remoteCalendars = caldavClient.discoverCalendars(credentials).excludeScheduling()
        calendarDao.upsert(remoteCalendars.map { it.toEntity(accountId) })
        remoteCalendars.map { it.url }.let { keepUrl ->
            calendarDao.deleteCalendarsNotExisting(accountId, keepUrl)
            calendarDao.getByAccountId(accountId).forEach {
                val remoteEvents = caldavClient.getEvents(credentials, it.url)
                eventDao.upsert(remoteEvents.map { event -> event.toEntity(it.id) })
            }
        }
    }

    private fun List<RemoteCalendar>.excludeScheduling() = filterNot { remote ->
        // Exclude scheduling calendars (RFC 6638 inbox/outbox)
        val url = remote.url.lowercase()
        url.contains("/inbox") || url.contains("/outbox")
    }

}
