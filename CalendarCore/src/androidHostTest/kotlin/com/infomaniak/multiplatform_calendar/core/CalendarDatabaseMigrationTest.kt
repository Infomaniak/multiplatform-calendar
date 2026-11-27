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
package com.infomaniak.multiplatform_calendar.core

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import com.infomaniak.multiplatform_calendar.core.data.local.CALENDAR_DATABASE_VERSION
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarDatabaseMigrationTest : RobolectricTestsBase() {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun migrateEveryVersionToLatest() = runTest {
        for (fromVersion in 1 until CALENDAR_DATABASE_VERSION) {
            val databaseName = "migration-from-$fromVersion.db"
            context.deleteDatabase(databaseName)

            try {
                val helper = MigrationTestHelper(
                    instrumentation = instrumentation,
                    file = context.getDatabasePath(databaseName),
                    driver = AndroidSQLiteDriver(),
                    databaseClass = CalendarDatabase::class,
                )

                helper.createDatabase(fromVersion).close()
                helper.runMigrationsAndValidate(CALENDAR_DATABASE_VERSION).close()
            } finally {
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun forgetsTheContactEtagsButKeepsTheContactsFromVersion3() = runTest {
        val databaseName = "etags-from-3.db"
        context.deleteDatabase(databaseName)

        try {
            val helper = MigrationTestHelper(
                instrumentation = instrumentation,
                file = context.getDatabasePath(databaseName),
                driver = AndroidSQLiteDriver(),
                databaseClass = CalendarDatabase::class,
            )

            helper.createDatabase(3).use {
                it.execSQL("INSERT INTO contactAccounts (id, etag) VALUES (42, 'etag')")
                it.execSQL(
                    "INSERT INTO contacts (accountId, email, name, avatarUrl, contactedTimes, isInAddressBook, nameNormalized, emailNormalized) " +
                        "VALUES (42, 'john@x.com', 'John', NULL, 5, 1, 'john', 'john@x.com')",
                )
            }
            helper.runMigrationsAndValidate(4).use { connection ->
                connection.prepare("SELECT etag FROM contactAccounts").use { statement ->
                    assertTrue(statement.step())
                    assertTrue(statement.isNull(0))
                }
                connection.prepare("SELECT name, contactedTimes FROM contacts").use { statement ->
                    assertTrue(statement.step())
                    assertEquals("John", statement.getText(0))
                    assertEquals(5, statement.getInt(1))
                }
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
