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

import com.infomaniak.multiplatform_calendar.core.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_core.organisations.OrganisationsManager
import com.infomaniak.multiplatform_core.organisations.domain.model.Organisation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.cancellation.CancellationException

@SingleIn(AppScope::class)
@Inject
public class CalendarOrganisationsManager internal constructor(
    private val calendarRepository: CalendarRepository,
    private val organisationsManager: OrganisationsManager,
    private val sdkCaller: SdkCaller,
) {

    /** The organisation [calendarId] belongs to, from the local organisations only. */
    @Throws(CalendarSdkException::class, CancellationException::class)
    public suspend fun getOrganisation(calendarId: CalendarId): Organisation? =
        sdkCaller(operation = "get the organisation of calendar $calendarId") {
            val calendar = calendarRepository.getCalendar(calendarId)
            calendar.organisationId?.let { organisationsManager.getOrganisation(calendar.accountId, it) }
        }
}
