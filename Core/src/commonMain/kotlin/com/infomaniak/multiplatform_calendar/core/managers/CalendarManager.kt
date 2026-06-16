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
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@SingleIn(AppScope::class)
@Inject
public class CalendarManager internal constructor(
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun observeCalendars(): Flow<List<Calendar>> {
        return accountRepository.currentAccountIdFlow.filterNotNull().flatMapLatest { accountId ->
            calendarRepository.observeCalendars(accountId)
        }.catch {
            //TODO: handle error
        }
    }

    public fun observeEvents(calendarId: CalendarId): Flow<List<Event>> {
        return calendarRepository.observeEvents(calendarId).catch {
            //TODO: handle error
        }
    }

    /** Observe events from all *visible* calendars of the current account overlapping [start, end[. */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    public fun observeEvents(start: Instant, end: Instant): Flow<List<Event>> {
        return accountRepository.currentAccountIdFlow.filterNotNull().flatMapLatest { accountId ->
            calendarRepository.observeVisibleEvents(accountId, start, end)
        }.catch {
            //TODO: handle error
        }
    }

    @Throws(CancellationException::class)
    public suspend fun syncCalendars(accountId: AccountId): Unit = withContext(Dispatchers.Default) {
        accountRepository.getCredentials(accountId)?.let { credentials ->
            calendarRepository.syncCalendars(accountId = accountId, credentials = credentials)
        }
    }

    public suspend fun deleteEvent(eventId: EventId): Unit = withContext(Dispatchers.Default) {
        accountRepository.currentAccountIdFlow.first()
            ?.let(accountRepository::getCredentials)
            ?.let { credentials -> calendarRepository.deleteEvent(credentials, eventId) }
    }

    public fun observeEvent(eventId: EventId): Flow<Event?> {
        return calendarRepository.observeEvent(eventId).catch {
            //TODO: handle error
        }
    }
}

