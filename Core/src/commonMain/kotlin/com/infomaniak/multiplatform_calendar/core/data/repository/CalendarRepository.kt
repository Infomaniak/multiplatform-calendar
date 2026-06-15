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

import com.infomaniak.multiplatform_calendar.core.data.local.dao.CalendarDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
internal class CalendarRepository(
    private val caldavClient: CalendarSyncRemoteSource,
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
) {

    fun observeCalendars(accountId: AccountId): Flow<List<Calendar>> {
        return calendarDao.observeByAccountId(accountId).map { entities ->
            entities.map(CalendarEntity::toDomain)
        }
    }

    fun observeEvents(calendarId: CalendarId): Flow<List<Event>> {
        val calendarFlow = calendarDao.getByCalendarId(calendarId)
        val eventsFlow = eventDao.getByCalendarId(calendarId)

        return combine(calendarFlow.filterNotNull(), eventsFlow) { calendarEntity, eventEntities ->
            val calendar = calendarEntity.toDomain()
            eventEntities.map { it.toDomain(calendar) }
        }
    }

    suspend fun syncCalendars(
        accountId: AccountId,
        credentials: DavAccount,
    ) {
        val remoteCalendars = caldavClient.discoverCalendars(credentials).excludeScheduling()
        calendarDao.upsert(remoteCalendars.map { it.toEntity(accountId) })
        remoteCalendars.map { CalendarId(it.url) }.let { keepIds ->
            calendarDao.deleteCalendarsNotExisting(accountId, keepIds)
            calendarDao.getByAccountId(accountId).forEach {
                val remoteEvents = caldavClient.getEvents(credentials, it.id.url)
                eventDao.upsert(remoteEvents.mapNotNull { event -> event.toEntity(it.id) })
            }
        }
    }

    private fun List<RemoteDavCalendar>.excludeScheduling() = filterNot { remote ->
        // Exclude scheduling calendars (RFC 6638 inbox/outbox)
        val url = remote.url.lowercase()
        url.contains("/inbox") || url.contains("/outbox")
    }

}
