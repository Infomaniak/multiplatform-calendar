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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEvents
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
        val calendarFlow = calendarDao.observeCalendar(calendarId)
        val eventsFlow = eventDao.observeEvents(calendarId)

        return combine(calendarFlow.filterNotNull(), eventsFlow) { calendarEntity, eventEntities ->
            val calendar = calendarEntity.toDomain()
            eventEntities.map { it.toDomain(calendar) }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun observeVisibleEvents(accountId: AccountId, start: Instant, end: Instant): Flow<List<Event>> {
        // TODO: Timezones are not handled yet — range bounds are compared in UTC.
        val startLocalDateTime = start.toLocalDateTime(TimeZone.UTC)
        val endLocalDateTime = end.toLocalDateTime(TimeZone.UTC)

        return eventDao.observeVisibleInRange(accountId, startLocalDateTime, endLocalDateTime)
            .map(List<EventWithCalendarEntity>::toDomainEvents)
    }

    suspend fun syncCalendars(
        accountId: AccountId,
        credentials: DavAccount,
    ) {
        val remoteCalendars = getCalendars(credentials) ?: return

        calendarDao.upsert(remoteCalendars.map { it.toEntity(accountId) })
        remoteCalendars.map { CalendarId(it.url) }.let { keepIds ->
            calendarDao.deleteCalendarsNotExisting(accountId, keepIds)
            calendarDao.getByAccountId(accountId).forEach {
                val remoteEvents = getRemoteEvents(credentials, it.id) ?: return@forEach
                eventDao.upsert(remoteEvents.mapNotNull { event -> eventToEntity(event, it.id) })
            }
        }
    }

    private suspend fun getCalendars(
        credentials: DavAccount,
    ): List<RemoteDavCalendar>? = getOrNull { caldavClient.discoverCalendars(credentials).excludeScheduling() }

    private suspend fun getRemoteEvents(
        credentials: DavAccount,
        id: CalendarId,
    ): List<RemoteDavEvent>? = getOrNull { caldavClient.getEvents(credentials, id.url) }

    private fun eventToEntity(
        event: RemoteDavEvent,
        calendarId: CalendarId,
    ): EventEntity? = getOrNull { event.toEntity(calendarId) }

    suspend fun deleteEvent(credentials: DavAccount, eventId: EventId) {
        eventDao.getEvent(eventId)?.let { event ->
            val _ = getOrNull { caldavClient.deleteEvent(credentials, eventId.url, event.etag) }
            eventDao.deleteEvent(eventId)
        }
    }

    fun observeEvent(eventId: EventId): Flow<Event?> {
        return eventDao.observeEventWithCalendar(eventId).map { rows ->
            rows.firstOrNull()?.let { row -> row.event.toDomain(row.calendar.toDomain()) }
        }
    }

    private inline fun <T> getOrNull(block: () -> T): T? =
        runCatching { block() }
            .cancellable()
            .logFailuresToSentry()
            .getOrNull()

    private fun List<RemoteDavCalendar>.excludeScheduling() = filterNot { remote ->
        // Exclude scheduling calendars (RFC 6638 inbox/outbox)
        val url = remote.url.lowercase()
        url.contains("/inbox") || url.contains("/outbox")
    }
}
