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

import com.infomaniak.multiplatform_calendar.core.data.local.dao.SettingsDao
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarDisabledNotificationTopicEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.column
import com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.CalendarNotificationSettings
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.NotificationTopic
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
internal class SettingsRepository(private val settingsDao: SettingsDao) {

    fun observeAccountNotificationsEnabled(accountId: AccountId): Flow<Boolean> {
        return settingsDao.observeAccountNotificationsEnabled(accountId).map { it ?: true }
    }

    suspend fun setAccountNotificationsEnabled(accountId: AccountId, enabled: Boolean) {
        settingsDao.setAccountNotificationsEnabled(accountId, enabled)
    }

    fun observeCalendarNotificationSettings(accountIds: Set<AccountId>): Flow<Map<CalendarId, CalendarNotificationSettings>> {
        return settingsDao.observeCalendarNotificationSettings(accountIds).map { rows ->
            rows.associate { it.calendarId to it.toDomain() }
        }
    }

    suspend fun setCalendarNotificationsEnabled(calendarId: CalendarId, enabled: Boolean) {
        settingsDao.setCalendarNotificationsEnabled(calendarId, enabled)
    }

    suspend fun setCalendarNotificationTopicEnabled(calendarId: CalendarId, topic: NotificationTopic, enabled: Boolean) {
        if (enabled) {
            settingsDao.deleteDisabledNotificationTopic(calendarId, topic.column)
        } else {
            settingsDao.insertDisabledNotificationTopic(CalendarDisabledNotificationTopicEntity(calendarId, topic.column))
        }
    }
}
