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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventDotColorInRange
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEvent
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEventWithOverrides
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomainEventsWithOverrides
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEditData
import com.infomaniak.multiplatform_calendar.core.data.mapper.toOverrideEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteEdit
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRemoteRecurrenceId
import com.infomaniak.multiplatform_calendar.core.data.mapper.toSyncedUpsert
import com.infomaniak.multiplatform_calendar.core.data.repository.utils.foldToDailyDotColors
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.DotColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.DateListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventDaySlice
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventWithOverrides
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceTarget
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.UpcomingAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.upcomingAlarms
import com.infomaniak.multiplatform_calendar.core.domain.model.event.expandRecurrencesInWindow
import com.infomaniak.multiplatform_calendar.core.domain.model.event.groupDaySlicesByDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.rebasedOnto
import com.infomaniak.multiplatform_calendar.core.domain.model.event.withSeriesChanges
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.resolveOccurrence
import com.infomaniak.multiplatform_calendar.core.domain.model.event.startsBefore
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toIcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toLocalStart
import com.infomaniak.multiplatform_calendar.core.domain.model.event.truncateBefore
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
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
     * [accountIds], the [DotColor] entries of those events; days with no event are omitted.
     *
     * Unlike [observeVisibleDaySlices] this never builds domain events nor [EventDaySlice]s: only the
     * [EventDotColorInRange] projection is read and folded, and each recurring master is expanded into its
     * RRULE occurrences. Multi-day events/occurrences still mark every covered day.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVisibleDotColorsByDay(
        accountIds: Set<AccountId>,
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
    ): Flow<Map<LocalDate, List<DotColor>>> {
        return eventDao.observeVisibleDotColorsInRange(
            accountIds = accountIds,
            startInstantMs = start.toEpochMilliseconds(),
            endInstantMs = end.toEpochMilliseconds(),
            startLocalDateTime = start.toLocalDateTime(timeZone),
            endLocalDateTime = end.toLocalDateTime(timeZone),
        ).mapLatest { rows ->
            rows.foldToDailyDotColors(
                rangeStart = start,
                rangeEnd = end,
                timeZone = timeZone,
                onExpansionTruncated = ::logTruncatedExpansion,
                onInvalidRange = ::logInvalidDotColorsRange,
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

    private fun logInvalidDotColorsRange(
        rangeStart: Instant,
        rangeEnd: Instant,
        timeZone: TimeZone,
        fromDay: LocalDate,
        toDay: LocalDate,
    ) {
        crashReport.capture(
            message = "Invalid day range computed while building dot colors by day",
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeOccurrence(
        occurrenceId: OccurrenceId,
        timeZone: TimeZone,
    ): Flow<Event?> {
        return when (occurrenceId) {
            is OccurrenceId.Master -> observeEvent(occurrenceId.masterId)
            is OccurrenceId.Recurrence -> observeEventWithOverrides(occurrenceId.masterId).mapLatest { eventWithOverrides ->
                eventWithOverrides?.resolveOccurrence(
                    occurrenceId = occurrenceId,
                    timeZone = timeZone,
                    onExpansionTruncated = ::logTruncatedExpansion,
                    onOrphanOverrideDropped = ::logOrphanOverride,
                )
            }.flowOn(Dispatchers.Default)
        }
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
        writePatchedEvent(credentials, eventId, entity, data, patched)
    }

    /** Send a patched resource off, moving it to another calendar when that is what the edit asks. */
    private suspend fun writePatchedEvent(
        credentials: DavAccount,
        eventId: EventId,
        entity: EventEntity,
        data: EventEditData,
        patched: RemoteDavEvent,
    ) {
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
     * Delete what [target] designates: the whole resource, or as far along its series as the scope it
     * carries reaches.
     *
     * A scoped delete is refused unless the target still names a live occurrence: the id travels
     * through clients as a string ([OccurrenceId.parse]), and both scoped paths read the date it
     * carries as a pivot, so a stale one would bound the series on a slot it never had.
     */
    suspend fun deleteEvent(credentials: DavAccount, target: OccurrenceTarget) {
        val occurrenceId = target.occurrenceId
        when {
            target !is OccurrenceTarget.Recurring -> deleteEvent(credentials, occurrenceId.masterId)
            occurrenceId !is OccurrenceId.Recurrence -> deleteEvent(credentials, occurrenceId.masterId)
            target.scope == RecurrenceScope.AllOccurrences -> deleteEvent(credentials, occurrenceId.masterId)
            !namesALiveOccurrence(occurrenceId) -> Unit
            target.scope == RecurrenceScope.ThisOccurrence -> excludeOccurrence(credentials, occurrenceId)
            else -> truncateSeriesFrom(credentials, occurrenceId)
        }
    }

    /** Whether the series still hands [occurrenceId] out, read as the display reads it. */
    private suspend fun namesALiveOccurrence(occurrenceId: OccurrenceId.Recurrence): Boolean {
        val relation = eventDao.getEventWithCalendar(occurrenceId.masterId) ?: return false
        return relation.toDomainEventWithOverrides().resolveOccurrence(
            occurrenceId = occurrenceId,
            timeZone = TimeZone.currentSystemDefault(),
            onExpansionTruncated = ::logTruncatedExpansion,
            onOrphanOverrideDropped = ::logOrphanOverride,
        ) != null
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
     * Apply [data] to what [target] designates: the whole resource — which is all a plain event, and
     * a series named by its master, can mean — or as far along the series as the scope it carries.
     */
    suspend fun updateEvent(credentials: DavAccount, target: OccurrenceTarget, data: EventEditData) {
        val occurrenceId = target.occurrenceId
        when {
            target !is OccurrenceTarget.Recurring -> updateEvent(credentials, occurrenceId.masterId, data)
            occurrenceId !is OccurrenceId.Recurrence -> updateEvent(credentials, occurrenceId.masterId, data)
            target.scope == RecurrenceScope.AllOccurrences -> updateSeriesFrom(credentials, occurrenceId, data)
            target.scope == RecurrenceScope.ThisOccurrence -> overrideOccurrence(credentials, occurrenceId, data)
            else -> error("Editing ${target.scope} of a series is not supported yet")
        }
    }

    /**
     * Apply to the whole series an edit prepared on one of its occurrences: [data] carries that
     * occurrence's slot, so its timing is first expressed back on the master (see [rebasedOnto]).
     *
     * Detached overrides are VEVENTs of their own, which the master's fields do not reach (see
     * [withSeriesChanges]); each is replayed the same change in the same resource, so the series and
     * its exceptions are never left disagreeing between two requests.
     */
    private suspend fun updateSeriesFrom(
        credentials: DavAccount,
        occurrenceId: OccurrenceId.Recurrence,
        data: EventEditData,
    ) {
        val masterId = occurrenceId.masterId
        val (entity, previousIcs) = eventDao.getEventWithRawIcs(masterId) ?: return
        val before = entity.toEditData()
        val masterTiming = before.timing
        val zone = TimeZone.currentSystemDefault()
        // The start the occurrence was displayed with, which is what the edit was prepared against.
        val shownStart = eventDao.getOverrideOf(masterId, occurrenceId.recurrenceKey)
            ?.content?.timing?.dtStart
            ?: occurrenceId.recurrenceKey.toLocalStart(masterTiming, zone)
            ?: return

        val after = data.copy(timing = data.timing.rebasedOnto(masterTiming, shownStart))
        val now = Clock.System.now().toICalUtcDateTime()
        var patched = caldavClient.patchEventIcs(previousIcs, after.toRemoteEdit(stamp = now, previous = entity))

        val alarmsEdited = after.alarms != before.alarms
        eventDao.getOverridesOf(masterId).forEach { override ->
            val carried = override.toEditData(entity.calendarId).withSeriesChanges(before, after) ?: return@forEach
            patched = caldavClient.upsertOverrideIcs(
                patched.icsData,
                override.toRemoteRecurrenceId(masterTiming),
                carried.toOverrideEdit(
                    stamp = now,
                    previous = override.content,
                    alarms = if (alarmsEdited) AlarmListEdit.FromData else AlarmListEdit.Preserve,
                ),
            )
        }

        writePatchedEvent(credentials, masterId, entity, after, patched)
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
        val existing = eventDao.getOverrideOf(masterId, occurrenceId.recurrenceKey)
        val recurrenceId = existing?.toRemoteRecurrenceId(masterTiming)
            ?: occurrenceId.recurrenceKey.toRemoteRecurrenceId(masterTiming)
            ?: return

        val now = Clock.System.now().toICalUtcDateTime()
        val patched = caldavClient.upsertOverrideIcs(
            previousIcs,
            recurrenceId,
            data.toOverrideEdit(stamp = now, previous = existing?.content ?: entity.content),
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

    /**
     * The alarms of the *visible* calendars of [accountIds] going off in `[from, from + horizon]`,
     * soonest first, capped at [limit] and restricted to the [actions] asked for.
     *
     * The events are read over a window widened by [ALARM_OFFSET_SLACK] on either side, because an alarm
     * does not go off when its event happens: a reminder set a week ahead belongs to an event a week
     * past the horizon, and one relative to the end of an event can outlive it. Alarms are stored inside
     * the event they belong to rather than in a table of their own, so no query can tell how far to
     * widen — hence a fixed margin, which a reminder set further out than that would fall outside of.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUpcomingAlarms(
        accountIds: Set<AccountId>,
        from: Instant,
        horizon: Duration,
        limit: Int,
        actions: Set<AlarmAction>,
        timeZone: TimeZone,
    ): Flow<List<UpcomingAlarm>> {
        val until = from + horizon
        val windowStart = from - ALARM_OFFSET_SLACK
        val windowEnd = until + ALARM_OFFSET_SLACK

        return observeVisibleEventsWithOverrides(accountIds, windowStart, windowEnd, zone = timeZone)
            .mapLatest { eventsWithOverrides ->
                eventsWithOverrides
                    .expandRecurrencesInWindow(
                        rangeStart = windowStart,
                        rangeEnd = windowEnd,
                        timeZone = timeZone,
                        onExpansionTruncated = ::logTruncatedExpansion,
                        onOrphanOverrideDropped = ::logOrphanOverride,
                    )
                    .upcomingAlarms(
                        from = from,
                        until = until,
                        limit = limit,
                        actions = actions,
                        defaultZone = timeZone,
                    )
            }
            .flowOn(Dispatchers.Default)
    }

    private fun observeEventWithOverrides(eventId: EventId): Flow<EventWithOverrides?> {
        return eventDao.observeEventWithCalendar(eventId).map { relation -> relation?.toDomainEventWithOverrides() }
    }

    companion object {
        /** How far past the alarm window events are read, see [observeUpcomingAlarms]. */
        private val ALARM_OFFSET_SLACK = 31.days
    }
}
