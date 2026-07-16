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

import com.infomaniak.multiplatform_calendar.core.crashreporting.CrashReport
import com.infomaniak.multiplatform_calendar.core.crashreporting.CrashReportLevel
import com.infomaniak.multiplatform_calendar.core.data.local.dao.CalendarDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.applyEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntitiesPreservingLocalPrefs
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteEdit
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.cancellable
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.forEachParallelLimited
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.logFailuresToSentry
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.RustNetworkException
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventChangeRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

@SingleIn(AppScope::class)
@Inject
internal class CalendarRepository(
    private val caldavClient: CalendarSyncRemoteSource,
    private val calendarDao: CalendarDao,
    private val crashReport: CrashReport,
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
        syncCalendarMetadata(accountId, credentials)
        calendarDao.getByAccountId(accountId).forEach { calendarEntity ->
            runCatching {
                val remoteEvents = getRemoteEvents(credentials, calendarEntity.id)
                val entities = remoteEvents.mapNotNull { event ->
                    runCatching { event.toEntity(calendarEntity.id) }
                        .cancellable()
                        .onFailure { crashReport.capture(message = "Skip event ${event.url}", exception = it) }
                        .getOrNull()
                }
                eventDao.upsert(entities)
            }.cancellable().onFailure {
                if (it is RustNetworkException) throw it
                crashReport.capture(message = "Skip calendar ${calendarEntity.id}", exception = it)
            }
        }
    }

    suspend fun syncEvents(
        accountId: AccountId,
        credentials: DavAccount,
    ) {
        syncCalendarMetadata(accountId, credentials)
        calendarDao.getByAccountId(accountId).forEachParallelLimited(limit = 2) { calendarEntity ->
            runCatching {
                val syncResult = caldavClient.syncCollection(
                    credentials = credentials,
                    calendarUrl = calendarEntity.id.url,
                    syncToken = calendarEntity.syncToken,
                )
                syncResult.items.partition(RemoteEventChangeRef::isDeleted).let { (deleted, changed) ->
                    updateChangedEvents(credentials, calendarEntity.id, changed)
                    updateDeletedEvents(calendarEntity.id, deleted)
                }
                if (syncResult.syncToken != null && syncResult.syncToken != calendarEntity.syncToken) {
                    calendarDao.updateSyncToken(calendarEntity.id, syncResult.syncToken)
                }
            }.cancellable().onFailure {
                if (it is RustNetworkException) throw it
                it.logFailuresToSentry(message = "Skip calendar ${calendarEntity.id}")
            }
        }
    }

    suspend fun downloadEventsByRange(
        accountId: AccountId,
        credentials: DavAccount,
        start: Instant,
        end: Instant,
    ) {
        syncCalendarMetadata(accountId, credentials)
        val startValue = start.toICalUtcDateTime()
        val endValue = end.toICalUtcDateTime()

        calendarDao.getByAccountId(accountId).forEachParallelLimited(limit = 4) { calendarEntity ->
            runCatching {
                val rangeEvents = caldavClient.getEventsInRange(
                    credentials = credentials,
                    calendarUrl = calendarEntity.id.url,
                    start = startValue,
                    end = endValue,
                )
                upsertEventsByChangeType(calendarEntity.id, rangeEvents)
            }.cancellable().onFailure {
                if (it is RustNetworkException) throw it
                it.logFailuresToSentry(message = "Skip calendar ${calendarEntity.id}")
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

    private suspend fun syncCalendarMetadata(accountId: AccountId, credentials: DavAccount) {
        val remoteCalendars = getCalendars(credentials)
        calendarDao.syncCalendars(accountId) { existingCalendarsById ->
            remoteCalendars.toEntitiesPreservingLocalPrefs(accountId = accountId, existingByCalendarId = existingCalendarsById)
        }
    }

    private suspend fun updateChangedEvents(
        credentials: DavAccount,
        calendarId: CalendarId,
        changed: List<RemoteEventChangeRef>,
    ) {
        if (changed.isNotEmpty()) {
            // TODO[Optimize]: fetch batched events instead of all events at once
            val changedEvents = caldavClient.getEventsByUrls(
                credentials = credentials,
                calendarUrl = calendarId.url,
                eventUrls = changed.map { it.eventUrl },
            )
            upsertEventsByChangeType(calendarId, changedEvents)
        }
    }

    private suspend fun updateDeletedEvents(
        calendarId: CalendarId,
        deleted: List<RemoteEventChangeRef>,
    ) {
        if (deleted.isNotEmpty()) {
            val deletedEventIds = deleted.map { item -> EventId(item.eventUrl) }
            eventDao.deleteEvents(calendarId = calendarId, eventIds = deletedEventIds) // TODO[Optimize]: delete in batches
        }
    }

    private suspend fun upsertEventsByChangeType(calendarId: CalendarId, remoteEvents: List<RemoteDavEvent>) {
        if (remoteEvents.isEmpty()) return

        val entities = remoteEvents.mapNotNull { event ->
            runCatching { event.toEntity(calendarId) }
                .onFailure { crashReport.capture(message = "Skip event ${event.url}", exception = it) }
                .getOrNull()
        }
        if (entities.isNotEmpty()) {
            runCatching {
                eventDao.upsert(entities) // TODO[Optimize]: upsert in batches
            }.onFailure {
                // An error here is not critical, we can continue syncing other calendars
                // Only occurred when the database is corrupted, which is very rare, or when the user has been logged out.
                reportEventUpsertFailure(calendarId, it, entities)
            }
        }
    }

    private fun reportEventUpsertFailure(
        calendarId: CalendarId,
        throwable: Throwable,
        entities: List<EventEntity>,
    ) {
        crashReport.addBreadcrumb(
            message = "Failed to upsert events for calendar $calendarId",
            category = "database",
            level = CrashReportLevel.Error,
            data = mapOf(
                "exception" to throwable.stackTraceToString(),
                "calendarId" to calendarId.url,
                "eventCount" to entities.size.toString(),
            ),
        )
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
