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
package com.infomaniak.multiplatform_calendar.core.managers

import com.infomaniak.multiplatform_calendar.core.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.core.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.core.data.repository.EventRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.DotColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventDaySlice
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.UpcomingAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceEditScope
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.extensions.syncAccountsWithRestartingCollection
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@SingleIn(AppScope::class)
@Inject
public class CalendarManager internal constructor(
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val eventRepository: EventRepository,
    private val sdkCaller: SdkCaller,
) {

    private val nonEmptyAccountIdsFlow by lazy {
        accountRepository.currentAccountIdsFlow.filter { it.isNotEmpty() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun observeCalendars(): Flow<List<Calendar>> {
        return sdkCaller.flow(operation = "observe calendars") {
            nonEmptyAccountIdsFlow.flatMapLatest { accountIds ->
                calendarRepository.observeCalendars(accountIds)
            }
        }
    }

    /**
     * Observe events from all *visible* calendars of the current account overlapping [start, end[,
     * with multi-day events split into one [EventDaySlice] per day and the
     * result is grouped by day and sorted, ready for a planning grid (all-day first, then by start).
     *
     * [timeZone] is the zone the planning grid is displayed in (device zone by default); it is
     * forwarded to the repository so floating-event visibility, recurrence expansion and the day split
     * share the same zone.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun observeDaySlices(
        start: Instant,
        end: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<Map<LocalDate, List<EventDaySlice>>> {
        return sdkCaller.flow(operation = "observe day slices from $start to $end for zone $timeZone") {
            accountRepository.currentAccountIdsFlow.filter { it.isNotEmpty() }.flatMapLatest { accountIds ->
                eventRepository.observeVisibleDaySlices(accountIds, start, end, timeZone)
            }
        }
    }

    /**
     * Observe the alarms about to go off in `[from, from + horizon]` across the *visible* calendars of
     * the current account, soonest first, capped at [limit].
     *
     * Meant to feed local notifications: each [UpcomingAlarm] carries an [UpcomingAlarmId] identifying
     * that one firing, so a client can diff two consecutive emissions and only touch what changed.
     *
     * Only the [actions] asked for are returned, defaulting to the ones a device can act on; an
     * `EMAIL` alarm is the server's job, not the client's.
     *
     * A recurring event yields one alarm per occurrence for a relative trigger, and a single one for an
     * absolute trigger, which names one fixed point in time (RFC 5545 §3.8.6.3).
     *
     * This flow re-emits on database changes, not on the passing of time, and [from] is bound once when
     * this is called — re-collecting the same flow keeps the window it was built with. To advance it, call
     * this again.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun observeUpcomingAlarms(
        limit: Int,
        actions: Set<AlarmAction> = setOf(AlarmAction.Display, AlarmAction.Audio),
        horizon: Duration = 30.days,
        from: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<List<UpcomingAlarm>> {
        return sdkCaller.flow(operation = "observe $limit upcoming alarms from $from over $horizon") {
            nonEmptyAccountIdsFlow.flatMapLatest { accountIds ->
                eventRepository.observeUpcomingAlarms(accountIds, from, horizon, limit, actions, timeZone)
            }
        }
    }

    /**
     * Observe per-day dot colors for months from [startMonth] to [endMonth] (inclusive).
     *
     * The result contains one entry per day having at least one event from a visible calendar,
     * mapped to the [DotColor] entries of that day, reduced per calendar and per color: an event redefining
     * its color gets a dot of its own color, and two calendars sharing a color get a dot each.
     *
     * [timeZone] defines day boundaries for the returned map.
     *
     * [startMonth] must be less than or equal to [endMonth].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun observeMonthlyDotColors(
        startMonth: YearMonth,
        endMonth: YearMonth,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<Map<LocalDate, List<DotColor>>> {
        val start = startMonth.firstDay.atStartOfDayIn(timeZone)
        val end = endMonth.lastDay.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone) // Exclusive end: start of the next month

        return sdkCaller.flow(operation = "observe monthly dot colors for $startMonth to $endMonth in $timeZone") {
            require(start < end) { "Start month $startMonth must not be after end month $endMonth" }
            nonEmptyAccountIdsFlow.flatMapLatest { accountIds ->
                eventRepository.observeVisibleDotColorsByDay(accountIds, start, end, timeZone)
            }
        }
    }

    public fun observeOccurrence(
        occurrenceId: OccurrenceId,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<Event?> {
        return sdkCaller.flow(operation = "observe occurrence $occurrenceId") {
            eventRepository.observeOccurrence(occurrenceId, timeZone)
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun syncEvents(): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "sync events for all accounts") {
            accountRepository.syncAccountsWithRestartingCollection { accountId, credentials ->
                calendarRepository.syncEvents(accountId = accountId, credentials = credentials)
            }
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    public suspend fun downloadEventsByRange(
        start: Instant,
        end: Instant,
    ): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "download events from $start to $end") {
            accountRepository.syncAccountsWithRestartingCollection { accountId, credentials ->
                calendarRepository.downloadEventsByRange(
                    accountId = accountId,
                    credentials = credentials,
                    start = start,
                    end = end,
                )
            }
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun updateCalendar(calendarId: CalendarId, edit: CalendarEditData): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "update calendar $calendarId") {
            if (edit.hasAnyChanges) {
                val credentials = getCredentialsForCalendar(calendarId)
                calendarRepository.updateCalendar(credentials, calendarId, edit)
            }
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun createEvent(data: EventEditData): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "create event in calendar ${data.calendarId}") {
            val credentials = getCredentialsForCalendar(data.calendarId)
            eventRepository.createEvent(credentials, data)
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun updateEvent(eventId: EventId, data: EventEditData): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "update event $eventId") {
            val credentials = getCredentialsForCalendar(data.calendarId)
            eventRepository.updateEvent(credentials, eventId, data)
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun deleteEvent(eventId: EventId): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "delete event $eventId") {
            val accountId = eventRepository.getAccountIdByEventId(eventId)
            val credentials = accountRepository.getCredentials(accountId)
            eventRepository.deleteEvent(credentials, eventId)
        }
    }

    /**
     * Delete what [scope] designates of the occurrence [occurrenceId] identifies, as offered by
     * [recurrenceScopes][com.infomaniak.multiplatform_calendar.core.domain.model.event.Event.recurrenceScopes].
     */
    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun deleteEvent(
        occurrenceId: OccurrenceId,
        scope: RecurrenceEditScope = RecurrenceEditScope.AllOccurrences,
    ): Unit = withContext(Dispatchers.Default) {
        sdkCaller.run(operation = "delete $scope of event $occurrenceId") {
            val accountId = eventRepository.getAccountIdByEventId(occurrenceId.masterId)
            val credentials = accountRepository.getCredentials(accountId)
            eventRepository.deleteEvent(credentials, occurrenceId, scope)
        }
    }

    private suspend fun getCredentialsForCalendar(calendarId: CalendarId): DavAccount {
        val accountId = calendarRepository.getCalendar(calendarId).accountId
        return accountRepository.getCredentials(accountId)
    }
}
