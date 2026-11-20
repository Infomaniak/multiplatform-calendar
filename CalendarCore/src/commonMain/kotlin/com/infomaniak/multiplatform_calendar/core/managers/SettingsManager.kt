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
import com.infomaniak.multiplatform_calendar.core.data.repository.SettingsRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.CalendarNotificationSettings
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.NotificationTopic
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Settings of the accounts and their calendars, stored on the device only.
 * [CalendarManager.observeUpcomingAlarms] already leaves out the reminders turned off here.
 */
@SingleIn(AppScope::class)
@Inject
public class SettingsManager internal constructor(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val sdkCaller: SdkCaller,
) {

    /** `true` by default, and while the account is unknown. */
    public fun observeAccountNotificationsEnabled(accountId: AccountId): Flow<Boolean> {
        return sdkCaller.flow(operation = "observe account $accountId notifications enabled") {
            settingsRepository.observeAccountNotificationsEnabled(accountId)
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun setAccountNotificationsEnabled(accountId: AccountId, enabled: Boolean): Unit = withContext(Dispatchers.Default) {
        sdkCaller(operation = "set account $accountId notifications enabled to $enabled") {
            settingsRepository.setAccountNotificationsEnabled(accountId, enabled)
        }
    }

    /** Every calendar of the current accounts, including those left to the defaults. */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun observeCalendarNotificationSettings(): Flow<Map<CalendarId, CalendarNotificationSettings>> {
        return sdkCaller.flow(operation = "observe calendar notification settings") {
            accountRepository.currentAccountIdsFlow.filter { it.isNotEmpty() }.flatMapLatest { accountIds ->
                settingsRepository.observeCalendarNotificationSettings(accountIds)
            }
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun setCalendarNotificationsEnabled(calendarId: CalendarId, enabled: Boolean): Unit = withContext(Dispatchers.Default) {
        sdkCaller(operation = "set calendar $calendarId notifications enabled to $enabled") {
            settingsRepository.setCalendarNotificationsEnabled(calendarId, enabled)
        }
    }

    @Throws(CancellationException::class, CalendarSdkException::class)
    public suspend fun setCalendarNotificationTopicEnabled(
        calendarId: CalendarId,
        topic: NotificationTopic,
        enabled: Boolean,
    ): Unit = withContext(Dispatchers.Default) {
        sdkCaller(operation = "set calendar $calendarId notification topic $topic enabled to $enabled") {
            settingsRepository.setCalendarNotificationTopicEnabled(calendarId, topic, enabled)
        }
    }
}
