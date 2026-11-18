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

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.remote.AuthDataSource
import com.infomaniak.multiplatform_calendar.core.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.core.data.repository.SettingsRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.CalendarNotificationSettings
import com.infomaniak.multiplatform_calendar.core.managers.utils.SdkCaller
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.crashreport.NoOpCrashReport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SettingsManagerTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var accountRepository: AccountRepository
    private lateinit var manager: SettingsManager

    @BeforeTest
    fun setUp() {
        database = DatabaseProviderFactory.createTestDatabaseConfig()
            .getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
        accountRepository = AccountRepository(
            accountDao = database.accountDao(),
            authDataSource = AuthDataSource(HttpClient(MockEngine { error("AuthDataSource must not be called in this test") })),
            dropCaldavConnections = {},
        )
        manager = SettingsManager(accountRepository, SettingsRepository(database.settingsDao()), SdkCaller(NoOpCrashReport))
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    /** An account without calendars, so that only the account change itself can make the flow emit. */
    @Test
    fun observeCalendarNotificationSettings_emitsOnceTheLastAccountIsRemoved() = runTest(timeout = 10.seconds) {
        val accountId = AccountId(1)
        accountRepository.storeCredentials(accountId, DavAccount(baseUrl = "https://dav.example", username = "u", password = "p"))
        val emissions = Channel<Map<CalendarId, CalendarNotificationSettings>>(Channel.UNLIMITED)
        backgroundScope.launch { manager.observeCalendarNotificationSettings().collect(emissions::send) }
        assertEquals(emptyMap(), emissions.receive())

        accountRepository.removeCredentials(accountId)

        assertEquals(emptyMap(), emissions.receive())
    }
}
