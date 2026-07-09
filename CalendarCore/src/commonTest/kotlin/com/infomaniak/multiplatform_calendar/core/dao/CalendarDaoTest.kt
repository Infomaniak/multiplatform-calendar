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
package com.infomaniak.multiplatform_calendar.core.dao

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.core.data.local.dao.CalendarDao
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarDaoTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var calendarDao: CalendarDao

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
        accountDao = database.accountDao()
        calendarDao = database.calendarDao()
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun syncCalendars_passesExistingByIdToUpdaterAndUpsertsResult() = runTest {
        val accountId = AccountId(1)
        accountDao.insert(AccountEntity(id = accountId))
        val existing = calendar(id = "calendar://a", accountId = accountId, isVisible = false)
        calendarDao.upsert(listOf(existing))

        var seenByUpdater: Map<CalendarId, CalendarEntity>? = null
        calendarDao.syncCalendars(accountId) { current ->
            seenByUpdater = current
            listOf(
                current.getValue(existing.id).copy(displayName = "renamed"),
                calendar(id = "calendar://b", accountId = accountId, isVisible = true),
            )
        }

        assertEquals(mapOf(existing.id to existing), seenByUpdater)
        assertEquals(
            listOf("calendar://a" to "renamed", "calendar://b" to "Calendar calendar://b"),
            calendarDao.getByAccountId(accountId).sortedBy { it.id.url }.map { it.id.url to it.displayName },
        )
    }

    @Test
    fun syncCalendars_deletesCalendarsAbsentFromUpdaterResult() = runTest {
        val accountId = AccountId(1)
        accountDao.insert(AccountEntity(id = accountId))
        val kept = calendar(id = "calendar://kept", accountId = accountId, isVisible = true)
        val removed = calendar(id = "calendar://removed", accountId = accountId, isVisible = true)
        calendarDao.upsert(listOf(kept, removed))

        calendarDao.syncCalendars(accountId) { _ -> listOf(kept) }

        assertEquals(listOf(kept), calendarDao.getByAccountId(accountId))
    }

    @Test
    fun syncCalendars_scopesReadsAndDeletesToRequestedAccount() = runTest {
        val accountId = AccountId(1)
        val otherAccountId = AccountId(2)
        accountDao.insert(AccountEntity(id = accountId))
        accountDao.insert(AccountEntity(id = otherAccountId))
        val mine = calendar(id = "calendar://mine", accountId = accountId, isVisible = true)
        val other = calendar(id = "calendar://other", accountId = otherAccountId, isVisible = true)
        calendarDao.upsert(listOf(mine, other))

        var seenIds: Set<CalendarId> = emptySet()
        calendarDao.syncCalendars(accountId) { current ->
            seenIds = current.keys
            emptyList()
        }

        assertEquals(setOf(mine.id), seenIds)
        // Other account's row was neither read, upserted, nor deleted.
        assertEquals(listOf(other), calendarDao.getByAccountId(otherAccountId))
        assertEquals(emptyList(), calendarDao.getByAccountId(accountId))
    }

    private fun calendar(id: String, accountId: AccountId, isVisible: Boolean) = CalendarEntity(
        id = CalendarId(id),
        accountId = accountId,
        displayName = "Calendar $id",
        color = null,
        isVisible = isVisible,
    )
}
