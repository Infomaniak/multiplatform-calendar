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
package com.infomaniak.multiplatform_calendar.core.repository

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.repository.SettingsRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.CalendarNotificationSettings
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.NotificationTopic
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var repository: SettingsRepository

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
        repository = SettingsRepository(database.settingsDao())
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun observeAccountNotificationsEnabled_isTrueByDefault() = runTest {
        assertTrue(repository.observeAccountNotificationsEnabled(ACCOUNT_ID).first(), "unknown account")

        seedAccount(ACCOUNT_ID)

        assertTrue(repository.observeAccountNotificationsEnabled(ACCOUNT_ID).first(), "account without settings")
    }

    @Test
    fun setAccountNotificationsEnabled_isReadBack() = runTest {
        seedAccount(ACCOUNT_ID)

        repository.setAccountNotificationsEnabled(ACCOUNT_ID, enabled = false)
        assertFalse(repository.observeAccountNotificationsEnabled(ACCOUNT_ID).first())

        repository.setAccountNotificationsEnabled(ACCOUNT_ID, enabled = true)
        assertTrue(repository.observeAccountNotificationsEnabled(ACCOUNT_ID).first())
    }

    @Test
    fun observeCalendarNotificationSettings_hasEveryCalendarOfTheGivenAccounts() = runTest {
        seedAccount(ACCOUNT_ID)
        seedAccount(OTHER_ACCOUNT_ID)
        val untouchedId = seedCalendar(ACCOUNT_ID, "untouched")
        val disabledId = seedCalendar(ACCOUNT_ID, "disabled")
        val withoutRemindersId = seedCalendar(ACCOUNT_ID, "without-reminders")
        val otherAccountCalendarId = seedCalendar(OTHER_ACCOUNT_ID, "other")

        repository.setCalendarNotificationsEnabled(disabledId, enabled = false)
        repository.setCalendarNotificationTopicEnabled(withoutRemindersId, NotificationTopic.Reminders, enabled = false)
        repository.setCalendarNotificationsEnabled(otherAccountCalendarId, enabled = false)

        assertEquals(
            mapOf(
                untouchedId to CalendarNotificationSettings(isEnabled = true, disabledTopics = emptySet()),
                disabledId to CalendarNotificationSettings(isEnabled = false, disabledTopics = emptySet()),
                withoutRemindersId to CalendarNotificationSettings(isEnabled = true, disabledTopics = setOf(NotificationTopic.Reminders)),
            ),
            repository.observeCalendarNotificationSettings(setOf(ACCOUNT_ID)).first(),
        )
    }

    @Test
    fun setCalendarNotificationTopicEnabled_keepsTheCalendarSwitchAndCanBeUndone() = runTest {
        seedAccount(ACCOUNT_ID)
        val calendarId = seedCalendar(ACCOUNT_ID, "a")

        repository.setCalendarNotificationsEnabled(calendarId, enabled = false)
        repository.setCalendarNotificationTopicEnabled(calendarId, NotificationTopic.Reminders, enabled = false)
        repository.setCalendarNotificationTopicEnabled(calendarId, NotificationTopic.Reminders, enabled = false)
        assertEquals(
            CalendarNotificationSettings(isEnabled = false, disabledTopics = setOf(NotificationTopic.Reminders)),
            repository.observeCalendarNotificationSettings(setOf(ACCOUNT_ID)).first()[calendarId],
        )

        repository.setCalendarNotificationTopicEnabled(calendarId, NotificationTopic.Reminders, enabled = true)
        repository.setCalendarNotificationsEnabled(calendarId, enabled = true)
        assertEquals(
            CalendarNotificationSettings(isEnabled = true, disabledTopics = emptySet()),
            repository.observeCalendarNotificationSettings(setOf(ACCOUNT_ID)).first()[calendarId],
        )
    }

    @Test
    fun deletingTheCalendarOrTheAccount_deletesItsSettings() = runTest {
        seedAccount(ACCOUNT_ID)
        val calendarId = seedCalendar(ACCOUNT_ID, "a")
        repository.setAccountNotificationsEnabled(ACCOUNT_ID, enabled = false)
        repository.setCalendarNotificationsEnabled(calendarId, enabled = false)
        repository.setCalendarNotificationTopicEnabled(calendarId, NotificationTopic.Reminders, enabled = false)

        database.calendarDao().deleteCalendarsNotExisting(ACCOUNT_ID, ids = emptyList())
        seedCalendar(ACCOUNT_ID, "a")
        assertEquals(
            CalendarNotificationSettings(isEnabled = true, disabledTopics = emptySet()),
            repository.observeCalendarNotificationSettings(setOf(ACCOUNT_ID)).first()[calendarId],
        )

        database.accountDao().delete(ACCOUNT_ID)
        seedAccount(ACCOUNT_ID)
        assertTrue(repository.observeAccountNotificationsEnabled(ACCOUNT_ID).first())
    }

    private suspend fun seedAccount(accountId: AccountId) = database.accountDao().insert(AccountEntity(accountId))

    private suspend fun seedCalendar(accountId: AccountId, name: String): CalendarId {
        val id = CalendarId("https://dav.example/cal/$name/")
        database.calendarDao().insert(CalendarEntity(id = id, accountId = accountId, displayName = name, color = null))
        return id
    }

    private companion object {
        val ACCOUNT_ID = AccountId(1)
        val OTHER_ACCOUNT_ID = AccountId(2)
    }
}
