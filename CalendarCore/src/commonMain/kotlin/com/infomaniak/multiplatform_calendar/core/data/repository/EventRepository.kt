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

import com.infomaniak.multiplatform_calendar.core.crashreporting.CrashReport
import com.infomaniak.multiplatform_calendar.core.crashreporting.CrashReportLevel
import com.infomaniak.multiplatform_calendar.core.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventCalendarColorInRange
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEvent
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEventsWithOverrides
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toSyncedUpsert
import com.infomaniak.multiplatform_calendar.core.data.repository.utils.foldToDailyCalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.VisibleCalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventDaySlice
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventWithOverrides
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.expandRecurrencesInWindow
import com.infomaniak.multiplatform_calendar.core.domain.model.event.groupDaySlicesByDay
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import com.infomaniak.multiplatform_calendar.core.extensions.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate
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
    private val crashReport: CrashReport,
) {

    /** The visible masters of the window, each with the overrides redefining one of its instances. */
    private fun observeVisibleEventsWithOverrides(
        accountIds: Set<AccountId>,
        start: Instant,
        end: Instant,
        zone: TimeZone,
    ): Flow<List<EventWithOverrides>> {
        // Range bounds are compared in two ways (see EventDao.observeVisibleInRange):
        // - Absolute epoch ms for anchored events (zoned / UTC / all-day).
        // - Wall-clock strings for floating events, re-interpreted in [zone] so a floating event
        //   stays visible at "10:00 local" wherever the user travels. Callers that also expand or
        //   group events by day (e.g. [observeVisibleDaySlices]) must pass the *same* zone here so
        //   the SQL filter and the downstream day split agree on which floating events are visible.
        return eventDao.observeVisibleInRange(
            accountIds = accountIds,
            startInstantMs = start.toEpochMilliseconds(),
            endInstantMs = end.toEpochMilliseconds(),
            startLocalDateTime = start.toLocalDateTime(zone),
            endLocalDateTime = end.toLocalDateTime(zone),
        ).map(List<EventWithCalendarEntity>::toDomainEventsWithOverrides)
    }

    fun observeVisibleEvents(
        accountIds: Set<AccountId>,
        start: Instant,
        end: Instant,
        zone: TimeZone,
    ): Flow<List<Event>> = observeVisibleEventsWithOverrides(accountIds, start, end, zone)
        .map { events -> events.map(EventWithOverrides::master) }

    /**
     * Like [observeVisibleEvents], but recurring masters are first expanded into their occurrences
     * (see [expandRecurrencesInWindow]) and each resulting event is split into one [EventDaySlice] per
     * day it covers (see [groupDaySlicesByDay]), then grouped by day and sorted for direct planning
     * display (all-day first, then by start time).
     *
     * [timeZone] drives the SQL wall-clock filter for floating events (forwarded to
     * [observeVisibleEvents]), the recurrence expansion anchor for floating / all-day occurrences
     * (RFC 5545 FORM #1) and the day split, so all three agree on which floating events are visible.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVisibleDaySlices(
        accountIds: Set<AccountId>,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
    ): Flow<Map<LocalDate, List<EventDaySlice>>> {
        return observeVisibleEventsWithOverrides(accountIds, start, end, zone = timeZone)
            .mapLatest { eventsWithOverrides ->
                eventsWithOverrides
                    .expandRecurrencesInWindow(start, end, timeZone, onExpansionTruncated = ::logTruncatedExpansion)
                    .groupDaySlicesByDay(start, end, timeZone)
            }
            .flowOn(Dispatchers.Default)
    }

    /**
     * For each day of `[start, end[` (in [timeZone]) that has at least one event from a *visible* calendar of
     * [accountIds], the per-calendar [VisibleCalendarColor] entries of calendars owning those events; days with no event
     * are omitted.
     *
     * Returning [VisibleCalendarColor] (stable calendar id + full [CalendarColors]) lets clients keep stable keys while
     * still picking whatever color variant they need (e.g. `datavizContainerVariant` for month-grid dots). Unlike
     * [observeVisibleDaySlices]
     * this never builds domain events nor [EventDaySlice]s: only the [EventCalendarColorInRange] projection is read and
     * folded, each recurring master is expanded into its RRULE occurrences, and each source color's palette is computed
     * once (cached) instead of once per event. Multi-day events/occurrences still mark every covered day.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVisibleCalendarColorsByDay(
        accountIds: Set<AccountId>,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
    ): Flow<Map<LocalDate, List<VisibleCalendarColor>>> {
        return eventDao.observeVisibleCalendarColorsInRange(
            accountIds = accountIds,
            startInstantMs = start.toEpochMilliseconds(),
            endInstantMs = end.toEpochMilliseconds(),
            startLocalDateTime = start.toLocalDateTime(timeZone),
            endLocalDateTime = end.toLocalDateTime(timeZone),
        ).mapLatest { rows ->
            rows.foldToDailyCalendarColors(
                rangeStart = start,
                rangeEnd = end,
                timeZone = timeZone,
                onExpansionTruncated = ::logTruncatedExpansion,
                onInvalidRange = ::logInvalidCalendarColorsRange,
            )
        }.flowOn(Dispatchers.Default)
    }

    private fun logTruncatedExpansion(masterId: EventId, outcome: ExpansionOutcome) {
        crashReport.capture(
            message = "Recurrence expansion hit a safety cap for event ${masterId.url}",
            data = mapOf("masterId" to masterId.url, "outcome" to outcome.name),
            level = CrashReportLevel.Warning,
        )
    }

    private fun logInvalidCalendarColorsRange(
        rangeStart: Instant,
        rangeEnd: Instant,
        timeZone: TimeZone,
        fromDay: LocalDate,
        toDay: LocalDate,
    ) {
        crashReport.capture(
            message = "Invalid day range computed while building calendar colors by day",
            data = mapOf(
                "rangeStart" to rangeStart.toString(),
                "rangeEnd" to rangeEnd.toString(),
                "timeZone" to timeZone.id,
                "fromDay" to fromDay.toString(),
                "toDay" to toDay.toString(),
            ),
            level = CrashReportLevel.Warning,
        )
    }

    fun observeEvent(eventId: EventId): Flow<Event?> {
        return eventDao.observeEventWithCalendar(eventId).map(EventWithCalendarEntity?::toDomainEvent)
    }

    suspend fun getAccountIdByEventId(eventId: EventId): AccountId {
        return accountDao.getAccountIdByEventId(eventId) ?: error("Event $eventId not found")
    }

    suspend fun createEvent(credentials: DavAccount, data: EventEditData) {
        val now = Clock.System.now().toICalUtcDateTime()
        val built = caldavClient.buildEventIcs(data.toRemoteEdit(stamp = now, previous = null))
        val ref = caldavClient.createEvent(credentials, data.calendarId.url, built.icsData)
        eventDao.upsertEventWithRawIcs(built.toSyncedUpsert(ref = ref, calendarId = data.calendarId))
    }

    suspend fun updateEvent(credentials: DavAccount, eventId: EventId, data: EventEditData) {
        val (entity, previousIcs) = eventDao.getEventWithRawIcs(eventId) ?: return
        val now = Clock.System.now().toICalUtcDateTime()
        // patchEventIcs returns the patched event reparsed from its final ICS, so the persisted
        // row mirrors exactly what is written to the server (bumped SEQUENCE, refreshed
        // DTSTAMP/LAST-MODIFIED, preserved server-only fields) with no Kotlin-side re-derivation.
        val patched = caldavClient.patchEventIcs(previousIcs, data.toRemoteEdit(stamp = now, previous = entity))
        if (data.calendarId == entity.calendarId) {
            val ref = caldavClient.updateEvent(credentials, eventId.url, entity.etag, patched.icsData)
            eventDao.upsertEventWithRawIcs(patched.toSyncedUpsert(ref = ref, calendarId = entity.calendarId))
        } else {
            updateCrossCalendarEvent(credentials, eventId, data, patched)
        }
    }

    suspend fun deleteEvent(credentials: DavAccount, eventId: EventId) {
        eventDao.getEvent(eventId)?.let { event ->
            caldavClient.deleteEvent(credentials, eventId.url, event.etag)
            eventDao.deleteEvent(eventId)
        }
    }

    private suspend fun updateCrossCalendarEvent(
        credentials: DavAccount,
        eventId: EventId,
        data: EventEditData,
        patched: RemoteDavEvent,
    ) {
        val ref = caldavClient.createEvent(credentials, data.calendarId.url, patched.icsData)
        deleteEvent(credentials, eventId)
        eventDao.upsertEventWithRawIcs(patched.toSyncedUpsert(ref = ref, calendarId = data.calendarId))
    }
}

