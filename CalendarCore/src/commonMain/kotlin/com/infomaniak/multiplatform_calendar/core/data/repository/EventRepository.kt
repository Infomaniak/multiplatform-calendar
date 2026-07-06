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
package com.infomaniak.multiplatform_calendar.core.data.repository

import com.infomaniak.multiplatform_calendar.core.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.applyEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEvent
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEvents
import com.infomaniak.multiplatform_calendar.core.data.mapper.toNewEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteEdit
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@SingleIn(AppScope::class)
@Inject
internal class EventRepository(
    private val accountDao: AccountDao,
    private val caldavClient: CalendarSyncRemoteSource,
    private val eventDao: EventDao,
) {

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

    fun observeEvent(eventId: EventId): Flow<Event?> {
        return eventDao.observeEventWithCalendar(eventId).map(EventWithCalendarEntity?::toDomainEvent)
    }

    suspend fun getAccountIdByEventId(eventId: EventId): AccountId {
        return accountDao.getAccountIdByEventId(eventId) ?: error("Event $eventId not found")
    }

    suspend fun createEvent(credentials: DavAccount, data: EventEditData) {
        val now = Clock.System.now().toICalUtcDateTime()
        val ics = caldavClient.buildEventIcs(data.toRemoteEdit(stamp = now))
        val ref = caldavClient.createEvent(credentials, data.calendarId.url, ics)
        eventDao.upsert(listOf(data.toNewEntity(ref = ref, rawIcs = ics)))
    }

    suspend fun updateEvent(credentials: DavAccount, eventId: EventId, data: EventEditData) {
        eventDao.getEvent(eventId)?.let { entity ->
            val now = Clock.System.now().toICalUtcDateTime()
            val newIcs = caldavClient.patchEventIcs(entity.rawIcs, data.toRemoteEdit(stamp = now))
            if (data.calendarId == entity.calendarId) {
                val ref = caldavClient.updateEvent(credentials, eventId.url, entity.etag, newIcs)
                eventDao.upsert(listOf(entity.applyEdit(data, etag = ref.etag, rawIcs = newIcs)))
            } else {
                updateCrossCalendarEvent(credentials, eventId, data, newIcs)
            }
        }
    }

    suspend fun deleteEvent(credentials: DavAccount, eventId: EventId) {
        eventDao.getEvent(eventId)?.let { event ->
            // TODO: Change when deleteEvent will return a result of success or failure
            caldavClient.deleteEvent(credentials, eventId.url, event.etag)
            eventDao.deleteEvent(eventId)
        }
    }

    private suspend fun updateCrossCalendarEvent(
        credentials: DavAccount,
        eventId: EventId,
        data: EventEditData,
        newIcs: String,
    ) {
        val ref = caldavClient.createEvent(credentials, data.calendarId.url, newIcs)
        deleteEvent(credentials, eventId)
        eventDao.upsert(listOf(data.toNewEntity(ref = ref, rawIcs = newIcs)))
    }
}
