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
package com.infomaniak.multiplatform_calendar.domain.useCases

import com.infomaniak.multiplatform_calendar.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.data.repository.CalendarRepository
import com.infomaniak.multiplatform_calendar.domain.model.calendar.AccountId
import dev.zacsweers.metro.Inject

@Inject
class SyncCalendars(
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
) {
    suspend operator fun invoke(accountId: AccountId) {
        accountRepository.getCredentials(accountId)?.let { credentials ->
            calendarRepository.syncCalendars(accountId = accountId, credentials = credentials)
        }
    }
}
