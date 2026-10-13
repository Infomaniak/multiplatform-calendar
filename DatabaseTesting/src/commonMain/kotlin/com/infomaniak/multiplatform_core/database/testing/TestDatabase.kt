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
package com.infomaniak.multiplatform_core.database.testing

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import com.infomaniak.multiplatform_core.database.DatabaseSource
import com.infomaniak.multiplatform_core.database.roomDatabase

/** An in-memory [T] on the SQLite driver of the test platform. */
public inline fun <reified T : RoomDatabase> testDatabase(
    noinline configure: RoomDatabase.Builder<T>.() -> Unit = {},
): T = roomDatabase(DatabaseSource.InMemory, testDriver(), configure)

/** The framework driver under Robolectric, the bundled one on Apple. */
public expect fun testDriver(): SQLiteDriver
