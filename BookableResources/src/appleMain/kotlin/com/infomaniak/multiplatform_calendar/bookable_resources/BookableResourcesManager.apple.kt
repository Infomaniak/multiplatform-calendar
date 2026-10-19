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
package com.infomaniak.multiplatform_calendar.bookable_resources

import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BusyPeriod
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesException
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

// Swift cannot use a value class inside a collection (it is boxed there), so these pass and return raw values.

@Throws(BookableResourcesException::class, CancellationException::class)
public suspend fun BookableResourcesManager.sync(accountIds: Set<Long> = emptySet()): Unit =
    sync(accountIds.mapTo(mutableSetOf(), ::AccountId))

@Throws(BookableResourcesException::class, CancellationException::class)
public suspend fun BookableResourcesManager.availabilityValues(
    accountId: AccountId,
    start: Instant,
    end: Instant,
    timeZone: TimeZone,
    rrule: String? = null,
): Map<String, Boolean> = availability(accountId, start, end, timeZone, rrule).mapKeys { (uuid, _) -> uuid.value }

@Throws(BookableResourcesException::class, CancellationException::class)
public suspend fun BookableResourcesManager.busyPeriods(
    accountId: AccountId,
    uuids: Set<String>,
    from: Instant,
    to: Instant,
): Map<String, List<BusyPeriod>> = busyPeriods(accountId, uuids.mapTo(mutableSetOf(), ::BookableResourceUuid), from, to)
    .mapKeys { (uuid, _) -> uuid.value }
