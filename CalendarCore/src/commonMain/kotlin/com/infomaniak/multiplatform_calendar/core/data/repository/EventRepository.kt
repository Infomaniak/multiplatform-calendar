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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventCalendarColorInRange
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEvent
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEventsWithOverrides
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEditData
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toOverrideEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteRecurrenceId
import com.infomaniak.multiplatform_calendar.core.data.mapper.toSyncedUpsert
import com.infomaniak.multiplatform_calendar.core.data.repository.utils.foldToDailyCalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.VisibleCalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.DateListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventDaySlice
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventWithOverrides
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.expandRecurrencesInWindow
import com.infomaniak.multiplatform_calendar.core.domain.model.event.groupDaySlicesByDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.rebasedOnto
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.shiftedBy
import com.infomaniak.multiplatform_calendar.core.domain.model.event.splitAt
import com.infomaniak.multiplatform_calendar.core.domain.model.event.startsBefore
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toIcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toLocalStart
import com.infomaniak.multiplatform_calendar.core.domain.model.event.truncateBefore
import com.infomaniak.multiplatform_calendar.core.domain.model.event.wallClockShift
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import com.infomaniak.multiplatform_calendar.core.extensions.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CalendarSyncRemoteSource
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        // - Absolute epoch ms for anchored events (zoned / UTC).
        // - Wall-clock strings for floating and all-day events, re-interpreted in [zone] so they stay
        //   visible at "10:00 local" (resp. on their own date) wherever the user travels. Callers that
        //   also expand or group events by day (e.g. [observeVisibleDaySlices]) must pass the *same* zone
        //   here so the SQL filter and the downstream day split agree on which events are visible.
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
                    .expandRecurrencesInWindow(
                        rangeStart = start,
                        rangeEnd = end,
                        timeZone = timeZone,
                        onExpansionTruncated = ::logTruncatedExpansion,
                        onOrphanOverrideDropped = ::logOrphanOverride,
                    )
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
                onOrphanOverrideDropped = ::logOrphanOverride,
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

    private fun logOrphanOverride(masterId: EventId, slot: RecurrenceKey) {
        crashReport.capture(
            message = "Override outside the recurrence range for event ${masterId.url}",
            data = mapOf("masterId" to masterId.url, "recurrenceKey" to slot.canonical),
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

    /**
     * Delete what [scope] designates of the series [occurrenceId] belongs to, the whole resource by
     * default — which is also what a plain event, and a master reached by its id, can only mean.
     */
    suspend fun deleteEvent(
        credentials: DavAccount,
        occurrenceId: OccurrenceId,
        scope: RecurrenceEditScope = RecurrenceEditScope.AllOccurrences,
    ) {
        when {
            occurrenceId !is OccurrenceId.Recurrence -> deleteEvent(credentials, occurrenceId.masterId)
            scope == RecurrenceEditScope.AllOccurrences -> deleteEvent(credentials, occurrenceId.masterId)
            scope == RecurrenceEditScope.ThisOccurrence -> excludeOccurrence(credentials, occurrenceId)
            else -> truncateSeriesFrom(credentials, occurrenceId)
        }
    }

    /**
     * Exclude one occurrence from its series: its date joins the master's `EXDATE`, and the override
     * redefining it — if any — is dropped in the very same PUT, so the resource never holds an
     * instance the rule no longer generates.
     */
    private suspend fun excludeOccurrence(credentials: DavAccount, occurrenceId: OccurrenceId.Recurrence) {
        val masterId = occurrenceId.masterId
        val (entity, previousIcs) = eventDao.getEventWithRawIcs(masterId) ?: return
        val editData = entity.toEditData()
        val excluded = occurrenceId.recurrenceKey.toIcalDateValue(editData.timing) ?: return
        val overrides = eventDao.getOverridesOf(masterId)

        val now = Clock.System.now().toICalUtcDateTime()
        val patched = caldavClient.patchEventIcs(
            previousIcs,
            editData.toRemoteEdit(
                stamp = now,
                previous = entity,
                exDates = DateListEdit.Set(entity.exDates + excluded),
                droppedOverrides = listOf(occurrenceId.recurrenceKey),
                knownOverrides = overrides,
                alarms = AlarmListEdit.Preserve,
            ),
        )
        val ref = caldavClient.updateEvent(credentials, masterId.url, entity.etag, patched.icsData)
        eventDao.upsertEventWithRawIcs(patched.toSyncedUpsert(ref = ref, calendarId = entity.calendarId))
    }

    /**
     * End the series right before [occurrenceId]: the rule is bounded on the preceding instance and
     * everything the dropped tail carried — its `EXDATE`/`RDATE` values and its overrides — goes in
     * the very same PUT, so no exception outlives the instances it applied to.
     *
     * When nothing at all precedes the pivot the whole resource is deleted instead: no recurrence
     * set describes a series with no instance.
     */
    private suspend fun truncateSeriesFrom(credentials: DavAccount, occurrenceId: OccurrenceId.Recurrence) {
        val masterId = occurrenceId.masterId
        val (entity, previousIcs) = eventDao.getEventWithRawIcs(masterId) ?: return
        val editData = entity.toEditData()
        val timing = editData.timing
        val zone = TimeZone.currentSystemDefault()
        val pivotStart = occurrenceId.recurrenceKey.toLocalStart(timing, zone) ?: return
        val overrides = eventDao.getOverridesOf(masterId)
        val truncated = timing.truncateBefore(pivotStart, zone) ?: return deleteEvent(credentials, masterId)

        val now = Clock.System.now().toICalUtcDateTime()
        val patched = caldavClient.patchEventIcs(
            previousIcs,
            editData.copy(timing = timing.copy(recurrenceRule = truncated.rule)).toRemoteEdit(
                stamp = now,
                previous = entity,
                exDates = DateListEdit.Set(timing.exDates.filter { it.startsBefore(pivotStart, timing) }),
                rDates = DateListEdit.Set(timing.rDates.filter { it.startsBefore(pivotStart, timing) }),
                droppedOverrides = overrides
                    .map(EventOverrideEntity::recurrenceKey)
                    .filterNot { it.startsBefore(pivotStart, timing) },
                knownOverrides = overrides,
                alarms = AlarmListEdit.Preserve,
            ),
        )
        val ref = caldavClient.updateEvent(credentials, masterId.url, entity.etag, patched.icsData)
        eventDao.upsertEventWithRawIcs(patched.toSyncedUpsert(ref = ref, calendarId = entity.calendarId))
    }

    /**
     * Apply [data] to what [scope] designates of the series [occurrenceId] belongs to, the whole
     * resource by default — which is also what a plain event, and a master reached by its id, can
     * only mean.
     */
    suspend fun updateEvent(
        credentials: DavAccount,
        occurrenceId: OccurrenceId,
        data: EventEditData,
        scope: RecurrenceEditScope = RecurrenceEditScope.AllOccurrences,
    ) {
        when {
            occurrenceId !is OccurrenceId.Recurrence -> updateEvent(credentials, occurrenceId.masterId, data)
            scope == RecurrenceEditScope.AllOccurrences -> updateSeriesFrom(credentials, occurrenceId, data)
            scope == RecurrenceEditScope.ThisOccurrence -> overrideOccurrence(credentials, occurrenceId, data)
            else -> splitSeriesAt(credentials, occurrenceId, data)
        }
    }

    /**
     * Split the series at [occurrenceId]: the instances from there on leave for a resource of their
     * own carrying [data], while the master is ended right before the pivot.
     *
     * RFC 5545 gives a series a single `RRULE` (§3.8.5.3), so a tail that reads differently cannot
     * stay in place. The new resource is written first: should the second request fail, the tail then
     * shows twice — visible, and undoable — where the other order would have lost it outright.
     *
     * Everything the tail carried travels with it, its `EXDATE`/`RDATE` values and its overrides, each
     * moved by however far the edit moved the pivot so they go on designating what they designated.
     */
    private suspend fun splitSeriesAt(
        credentials: DavAccount,
        occurrenceId: OccurrenceId.Recurrence,
        data: EventEditData,
    ) {
        val masterId = occurrenceId.masterId
        val entity = eventDao.getEvent(masterId) ?: return
        val timing = entity.toEditData().timing
        val zone = TimeZone.currentSystemDefault()
        val pivotStart = occurrenceId.recurrenceKey.toLocalStart(timing, zone) ?: return
        val split = timing.splitAt(pivotStart, zone)
        // Nothing precedes the pivot, so there is no head to leave behind: the whole series is moving.
        if (split.head == null) return updateSeriesFrom(credentials, occurrenceId, data)

        val tailTiming = data.timing.copy(recurrenceRule = split.tail)
        val tail = data.copy(timing = tailTiming)
        val delta = wallClockShift(from = pivotStart, to = tailTiming.start)
        val now = Clock.System.now().toICalUtcDateTime()
        val built = caldavClient.buildEventIcs(
            tail.toRemoteEdit(
                stamp = now,
                previous = null,
                exDates = DateListEdit.Set(timing.exDates.movedTail(pivotStart, timing, delta)),
                rDates = DateListEdit.Set(timing.rDates.movedTail(pivotStart, timing, delta)),
            ),
        )

        val resource = carryOverridesInto(built, masterId, timing, pivotStart, tail, delta, now)
        val ref = caldavClient.createEvent(credentials, data.calendarId.url, resource.icsData)
        eventDao.upsertEventWithRawIcs(resource.toSyncedUpsert(ref = ref, calendarId = data.calendarId))

        // What the master keeps is exactly what a "delete this and following" would have left it.
        truncateSeriesFrom(credentials, occurrenceId)
    }

    /** The overrides of [masterId] the [tail] takes over, redefined instance by instance in [built]. */
    private suspend fun carryOverridesInto(
        built: RemoteDavEvent,
        masterId: EventId,
        masterTiming: EventTiming,
        pivotStart: LocalDateTime,
        tail: EventEditData,
        delta: Duration,
        stamp: String,
    ): RemoteDavEvent {
        var resource = built
        eventDao.getOverridesOf(masterId)
            .filterNot { it.recurrenceKey.startsBefore(pivotStart, masterTiming) }
            .forEach { override ->
                val key = override.recurrenceKey.shiftedBy(delta)
                val recurrenceId = key.toRemoteRecurrenceId(tail.timing) ?: return@forEach
                val moved = override.toEditData(tail.calendarId).let { it.copy(timing = it.timing.shiftedBy(delta)) }
                resource = caldavClient.upsertOverrideIcs(
                    resource.icsData,
                    recurrenceId,
                    // Detached from the tail's own master, which is what it is about to be seeded from.
                    edit = moved.toOverrideEdit(
                        stamp = stamp,
                        previousColorArgb = tail.eventColor?.argb,
                        previousAlarms = tail.alarms.map(EventAlarm::toEntity),
                    ),
                )
            }

        return resource
    }

    /**
     * Apply to the whole series an edit prepared on one of its occurrences: [data] carries that
     * occurrence's slot, so its timing is first expressed back on the master (see [rebasedOnto]).
     */
    private suspend fun updateSeriesFrom(
        credentials: DavAccount,
        occurrenceId: OccurrenceId.Recurrence,
        data: EventEditData,
    ) {
        val masterId = occurrenceId.masterId
        val entity = eventDao.getEvent(masterId) ?: return
        val masterTiming = entity.toEditData().timing
        val zone = TimeZone.currentSystemDefault()
        // The start the occurrence was displayed with, which is what the edit was prepared against.
        val shownStart = eventDao.getOverridesOf(masterId)
            .firstOrNull { it.recurrenceKey == occurrenceId.recurrenceKey }
            ?.content?.timing?.dtStart
            ?: occurrenceId.recurrenceKey.toLocalStart(masterTiming, zone)
            ?: return

        updateEvent(credentials, masterId, data.copy(timing = data.timing.rebasedOnto(masterTiming, shownStart)))
    }

    /**
     * Redefine one occurrence, as an override VEVENT living in its master's resource: the instance is
     * addressed by the `RECURRENCE-ID` the resource already names it with, and the whole object —
     * master and every override — is PUT back in one request.
     *
     * The calendar cannot change: an override has no resource of its own to move.
     */
    private suspend fun overrideOccurrence(
        credentials: DavAccount,
        occurrenceId: OccurrenceId.Recurrence,
        data: EventEditData,
    ) {
        val masterId = occurrenceId.masterId
        val (entity, previousIcs) = eventDao.getEventWithRawIcs(masterId) ?: return
        require(data.calendarId == entity.calendarId) {
            "Cannot move a single occurrence to another calendar: its override lives in $masterId"
        }
        val masterTiming = entity.toEditData().timing
        val existing = eventDao.getOverridesOf(masterId).firstOrNull { it.recurrenceKey == occurrenceId.recurrenceKey }
        val recurrenceId = existing?.toRemoteRecurrenceId(masterTiming)
            ?: occurrenceId.recurrenceKey.toRemoteRecurrenceId(masterTiming)
            ?: return

        val shown = existing?.content ?: entity.content
        val now = Clock.System.now().toICalUtcDateTime()
        val patched = caldavClient.upsertOverrideIcs(
            previousIcs,
            recurrenceId,
            data.toOverrideEdit(stamp = now, previousColorArgb = shown.colorArgb, previousAlarms = shown.alarms),
        )
        val ref = caldavClient.updateEvent(credentials, masterId.url, entity.etag, patched.icsData)
        eventDao.upsertEventWithRawIcs(patched.toSyncedUpsert(ref = ref, calendarId = entity.calendarId))
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


/**
 * The values of this list the tail takes over — those the pivot does not leave behind — each moved by
 * [delta] so it goes on designating the instance it designated on [master].
 */
private fun List<IcalDateValue>.movedTail(
    pivotStart: LocalDateTime,
    master: EventTiming,
    delta: Duration,
): List<IcalDateValue> = filterNot { it.startsBefore(pivotStart, master) }.map { it.shiftedBy(delta) }
