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

import com.infomaniak.multiplatform_calendar.bookable_resources.BookableResourcesManager
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResource
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.mapper.toResourceAvailabilityQuery
import com.infomaniak.multiplatform_calendar.core.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.appenvironment.AppEnvironment
import com.infomaniak.multiplatform_core.crashreport.CrashReport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/** The bookable resources of the account owning a calendar, to book them in the events of that calendar. */
@SingleIn(AppScope::class)
@Inject
public class CalendarBookableResourcesManager internal constructor(
    private val calendarRepository: CalendarRepository,
    database: CalendarDatabase,
    private val sdkCaller: SdkCaller,
    appEnvironment: AppEnvironment,
    crashReport: CrashReport,
) {

    internal val bookableResourcesManager = BookableResourcesManager(database, appEnvironment, crashReport)

    /** Sorted by name; empty while [calendarId] is missing. */
    public fun observeResources(calendarId: CalendarId): Flow<List<BookableResource>> =
        sdkCaller.flow(operation = "observe bookable resources for calendar $calendarId") {
            flowForAccountOf(calendarId, ifCalendarMissing = emptyList(), bookableResourcesManager::observeResources)
        }

    /**
     * A resource missing locally is fetched; null when that fails, the account cannot book it or [calendarId] is
     * missing.
     */
    public fun observeResource(calendarId: CalendarId, uuid: BookableResourceUuid): Flow<BookableResource?> =
        sdkCaller.flow(operation = "observe bookable resource $uuid for calendar $calendarId") {
            flowForAccountOf(calendarId, ifCalendarMissing = null) { bookableResourcesManager.observeResource(it, uuid) }
        }

    /**
     * Always hits the network. Recurring [timing] is checked over 3 months, on its rule only: the API takes no RDATE
     * nor EXDATE. A whole-day or floating [timing] is read in [timeZone].
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun availability(
        calendarId: CalendarId,
        timing: EventTiming,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Map<BookableResourceUuid, Boolean> = sdkCaller(operation = "check bookable resources availability for calendar $calendarId") {
        val query = timing.toResourceAvailabilityQuery(timeZone)
        bookableResourcesManager.availability(accountIdOf(calendarId), query.start, query.end, query.timeZone, query.rrule)
    }

    /** See [availability]. */
    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun isAvailable(
        calendarId: CalendarId,
        uuid: BookableResourceUuid,
        timing: EventTiming,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean = sdkCaller(operation = "check bookable resource $uuid availability for calendar $calendarId") {
        val query = timing.toResourceAvailabilityQuery(timeZone)
        bookableResourcesManager.isAvailable(accountIdOf(calendarId), uuid, query.start, query.end, query.timeZone, query.rrule)
    }

    /** Resources the account cannot book are left out. Hits the network unless [uuids] is empty. */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun busyPeriods(
        calendarId: CalendarId,
        uuids: Set<BookableResourceUuid>,
        from: Instant,
        to: Instant,
    ): Map<BookableResourceUuid, List<BusyPeriod>> = sdkCaller(operation = "read bookable resources busy periods for calendar $calendarId") {
        bookableResourcesManager.busyPeriods(accountIdOf(calendarId), uuids, from, to)
    }

    private suspend fun accountIdOf(calendarId: CalendarId) = calendarRepository.getCalendar(calendarId).accountId

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <R> flowForAccountOf(calendarId: CalendarId, ifCalendarMissing: R, block: (AccountId) -> Flow<R>): Flow<R> =
        calendarRepository.observeCalendar(calendarId)
            .map { it?.accountId }
            .distinctUntilChanged()
            .flatMapLatest { accountId -> accountId?.let(block) ?: flowOf(ifCalendarMissing) }
}
