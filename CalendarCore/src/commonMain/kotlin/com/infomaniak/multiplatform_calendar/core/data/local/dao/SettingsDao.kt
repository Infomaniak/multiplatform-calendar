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
package com.infomaniak.multiplatform_calendar.core.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountSettingsEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarDisabledNotificationTopicEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarSettingsEntity
import com.infomaniak.multiplatform_calendar.core.data.local.relation.CalendarNotificationSettingsWithTopics
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.Flow

/** Each setter inserts the default row first, then updates its own column, so that it never resets the other settings. */
@Dao
internal interface SettingsDao {

    @Query("SELECT notificationsEnabled FROM account_settings WHERE accountId = :accountId")
    fun observeAccountNotificationsEnabled(accountId: AccountId): Flow<Boolean?>

    @Transaction
    suspend fun setAccountNotificationsEnabled(accountId: AccountId, enabled: Boolean) {
        insertAccountSettings(AccountSettingsEntity(accountId))
        updateAccountNotificationsEnabled(accountId, enabled)
    }

    @Transaction
    @Query(
        """
        SELECT calendar.id AS calendarId, COALESCE(settings.notificationsEnabled, 1) AS notificationsEnabled
        FROM calendars calendar
        LEFT JOIN calendar_settings settings ON settings.calendarId = calendar.id
        WHERE calendar.accountId IN(:accountIds)
        """,
    )
    fun observeCalendarNotificationSettings(accountIds: Set<AccountId>): Flow<List<CalendarNotificationSettingsWithTopics>>

    @Transaction
    suspend fun setCalendarNotificationsEnabled(calendarId: CalendarId, enabled: Boolean) {
        insertCalendarSettings(CalendarSettingsEntity(calendarId))
        updateCalendarNotificationsEnabled(calendarId, enabled)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDisabledNotificationTopic(topic: CalendarDisabledNotificationTopicEntity)

    @Query("DELETE FROM calendar_disabled_notification_topics WHERE calendarId = :calendarId AND topic = :topic")
    suspend fun deleteDisabledNotificationTopic(calendarId: CalendarId, topic: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccountSettings(settings: AccountSettingsEntity)

    @Query("UPDATE account_settings SET notificationsEnabled = :enabled WHERE accountId = :accountId")
    suspend fun updateAccountNotificationsEnabled(accountId: AccountId, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCalendarSettings(settings: CalendarSettingsEntity)

    @Query("UPDATE calendar_settings SET notificationsEnabled = :enabled WHERE calendarId = :calendarId")
    suspend fun updateCalendarNotificationsEnabled(calendarId: CalendarId, enabled: Boolean)
}
