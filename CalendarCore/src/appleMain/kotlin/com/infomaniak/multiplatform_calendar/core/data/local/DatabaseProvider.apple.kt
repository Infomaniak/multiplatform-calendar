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
package com.infomaniak.multiplatform_calendar.core.data.local

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi

@SingleIn(scope = AppScope::class)
@Inject
internal actual class DatabaseProvider(private val databaseConfig: DatabaseConfig) {

    @OptIn(ExperimentalForeignApi::class)
    actual fun getRoomDatabaseBuilder(inMemory: Boolean): RoomDatabase.Builder<CalendarDatabase> {
        return when {
            inMemory -> Room.inMemoryDatabaseBuilder<CalendarDatabase>()
            else -> Room.databaseBuilder<CalendarDatabase>(
                name = databaseConfig.path,
            )
        }
    }
}

/**
 * Configuration for the database, containing the path where the database file should be stored.
 *
 * @param path Absolute path (including filename) where the database file should be stored.
 */
internal value class DatabaseConfig(val path: String)
