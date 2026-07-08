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
import com.infomaniak.multiplatform_calendar.core.data.mapper.applyEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntitiesPreservingLocalPrefs
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.logFailuresToSentry
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
internal class CalendarRepository(
    private val caldavClient: CalendarSyncRemoteSource,
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
) {

    fun observeCalendars(accountIds: Set<AccountId>): Flow<List<Calendar>> {
        return calendarDao.observeByAccountIds(accountIds).map { calendarEntities ->
            calendarEntities.map(CalendarEntity::toDomain)
        }
    }

    suspend fun getCalendars(
        credentials: DavAccount,
    ): List<RemoteDavCalendar> = caldavClient.discoverCalendars(credentials).excludeScheduling()

    suspend fun getCalendar(calendarId: CalendarId): Calendar {
        return calendarDao.findById(calendarId)?.toDomain() ?: error("Calendar $calendarId not found")
    }

    suspend fun syncCalendars(
        accountId: AccountId,
        credentials: DavAccount,
    ) {
        val remoteCalendars = getCalendars(credentials)
        calendarDao.syncCalendars(accountId) { existingCalendarsById ->
            remoteCalendars.toEntitiesPreservingLocalPrefs(accountId = accountId, existingByCalendarId = existingCalendarsById)
        }

            calendarDao.getByAccountId(accountId).forEach { calendarEntity ->
                val remoteEvents = getRemoteEvents(credentials, calendarEntity.id)
                val entities = remoteEvents.mapNotNull { event ->
                    runCatching { event.toEntity(calendarEntity.id) }
                        .onFailure { it.logFailuresToSentry(message = "Skip event ${event.url}") }
                        .getOrNull()
                }
                eventDao.upsert(entities)
        }
    }

    suspend fun updateCalendar(credentials: DavAccount, calendarId: CalendarId, edit: CalendarEditData) {
        calendarDao.findById(calendarId)?.let { calendarEntity ->
            if (edit.hasRemoteChanges) {
                caldavClient.updateCalendar(
                    credentials = credentials,
                    calendarUrl = calendarId.url,
                    edit = edit.toRemoteEdit(),
                )
            }
            calendarDao.update(calendar = calendarEntity.applyEdit(edit))
        }
    }

    private suspend fun getRemoteEvents(
        credentials: DavAccount,
        id: CalendarId,
    ): List<RemoteDavEvent> = caldavClient.getEvents(credentials, id.url)

    private fun List<RemoteDavCalendar>.excludeScheduling() = filterNot { remote ->
        // Exclude scheduling calendars (RFC 6638 inbox/outbox)
        val url = remote.url.lowercase()
        url.contains("/inbox") || url.contains("/outbox")
    }
}
