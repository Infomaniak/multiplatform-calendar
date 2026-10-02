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
package com.infomaniak.multiplatform_core.contacts.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.infomaniak.multiplatform_core.contacts.data.local.dao.ContactDao
import com.infomaniak.multiplatform_core.contacts.data.local.entity.ContactAccountEntity
import com.infomaniak.multiplatform_core.contacts.data.local.entity.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [ContactAccountEntity::class, ContactEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ContactsDatabaseConstructor::class)
internal abstract class ContactsDatabase : RoomDatabase() {
    internal abstract fun contactDao(): ContactDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object ContactsDatabaseConstructor : RoomDatabaseConstructor<ContactsDatabase> {
    override fun initialize(): ContactsDatabase
}

internal fun contactsDatabase(
    source: ContactsDatabaseSource,
    driver: SQLiteDriver? = null,
): ContactsDatabase = when (source) {
        is ContactsDatabaseSource.File -> Room.databaseBuilder<ContactsDatabase>(name = source.path)
        ContactsDatabaseSource.InMemory -> Room.inMemoryDatabaseBuilder<ContactsDatabase>()
    }
    .setDriver(driver ?: BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
