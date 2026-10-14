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
package com.infomaniak.multiplatform_core.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Opens the Room database [T] at [source] with the bundled SQLite driver, querying on [Dispatchers.IO].
 *
 * @param configure Extra builder settings, such as migrations.
 */
public inline fun <reified T : RoomDatabase> roomDatabase(
    source: DatabaseSource,
    noinline configure: RoomDatabase.Builder<T>.() -> Unit = {},
): T = databaseBuilder<T>(source).buildDatabase(configure)

/** Room declares its builders per platform only, with no common declaration to call them through. */
@PublishedApi
internal expect inline fun <reified T : RoomDatabase> databaseBuilder(source: DatabaseSource): RoomDatabase.Builder<T>

@PublishedApi
internal fun <T : RoomDatabase> RoomDatabase.Builder<T>.buildDatabase(
    configure: RoomDatabase.Builder<T>.() -> Unit,
): T = setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .apply(configure)
    .build()
