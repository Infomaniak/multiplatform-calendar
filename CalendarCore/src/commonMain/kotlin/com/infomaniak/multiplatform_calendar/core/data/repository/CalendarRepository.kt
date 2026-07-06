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
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventDaySlice
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.groupDaySlicesByDay
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.cancellable
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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

    fun observeVisibleEvents(accountIds: Set<AccountId>, start: Instant, end: Instant): Flow<List<Event>> {
        // Range bounds are compared in two ways (see EventDao.observeVisibleInRange):
        // - Absolute epoch ms for anchored events (zoned / UTC / all-day).
        // - Wall-clock strings for floating events, re-interpreted in the device's current zone,
        //   so a floating event stays visible at "10:00 local" wherever the user travels.
        val deviceZone = TimeZone.currentSystemDefault()
        return eventDao.observeVisibleInRange(
            accountIds = accountIds,
            startInstantMs = start.toEpochMilliseconds(),
            endInstantMs = end.toEpochMilliseconds(),
            startLocalDateTime = start.toLocalDateTime(deviceZone),
            endLocalDateTime = end.toLocalDateTime(deviceZone),
        ).map(List<EventWithCalendarEntity>::toDomainEvents)
    }

    /**
     * Like [observeVisibleEvents], but each multi-day event is split into one [EventDaySlice] per day
     * it covers (see [groupDaySlicesByDay]), then grouped by day and sorted for direct planning
     * display (all-day first, then by start time).
     *
     * [gridZone] should match the zone used for floating-event visibility in [observeVisibleEvents];
     * it defaults to the device zone for both.
     */
    fun observeVisibleDaySlices(
        accountIds: Set<AccountId>,
        start: Instant,
        end: Instant,
        gridZone: TimeZone,
    ): Flow<Map<LocalDate, List<EventDaySlice>>> {
        return observeVisibleEvents(accountIds, start, end).map { events ->
            events.groupDaySlicesByDay(start, end, gridZone)
        }
    }

    suspend fun syncCalendars(
        accountId: AccountId,
        credentials: DavAccount,
    ) {
        val remoteCalendars = getCalendars(credentials)

        calendarDao.upsert(remoteCalendars.map { it.toEntity(accountId) })
        remoteCalendars.map { CalendarId(it.url) }.let { keepIds ->
            calendarDao.deleteCalendarsNotExisting(accountId, keepIds)
            calendarDao.getByAccountId(accountId).forEach { calendarEntity ->
                val remoteEvents = getRemoteEvents(credentials, calendarEntity.id)
                eventDao.upsert(remoteEvents.map { event -> event.toEntity(calendarEntity.id) })
            }
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
