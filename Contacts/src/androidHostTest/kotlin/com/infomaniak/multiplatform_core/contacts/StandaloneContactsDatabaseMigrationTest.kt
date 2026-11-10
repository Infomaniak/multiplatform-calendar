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
package com.infomaniak.multiplatform_core.contacts

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.infomaniak.multiplatform_core.contacts.data.local.STANDALONE_CONTACTS_DATABASE_VERSION
import com.infomaniak.multiplatform_core.contacts.data.local.StandaloneContactsDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class StandaloneContactsDatabaseMigrationTest : RobolectricTestsBase() {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun migrateEveryVersionToLatest() = runTest {
        for (fromVersion in 1 until STANDALONE_CONTACTS_DATABASE_VERSION) {
            val databaseName = "migration-from-$fromVersion.db"
            context.deleteDatabase(databaseName)

            try {
                val helper = MigrationTestHelper(
                    instrumentation = instrumentation,
                    file = context.getDatabasePath(databaseName),
                    driver = BundledSQLiteDriver(),
                    databaseClass = StandaloneContactsDatabase::class,
                )

                helper.createDatabase(fromVersion).close()
                helper.runMigrationsAndValidate(STANDALONE_CONTACTS_DATABASE_VERSION).close()
            } finally {
                context.deleteDatabase(databaseName)
            }
        }
    }
}
