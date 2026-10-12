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
package com.infomaniak.multiplatform_calendar.meeting_rooms.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.dao.MeetingRoomDao
import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.entity.MeetingRoomEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [MeetingRoomEntity::class],
    version = 1,
    exportSchema = true,
)
@ColumnTypeConverters(MeetingRoomsConverters::class)
@ConstructedBy(MeetingRoomsDatabaseConstructor::class)
internal abstract class MeetingRoomsDatabase : RoomDatabase() {
    internal abstract fun meetingRoomDao(): MeetingRoomDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object MeetingRoomsDatabaseConstructor : RoomDatabaseConstructor<MeetingRoomsDatabase> {
    override fun initialize(): MeetingRoomsDatabase
}

internal fun meetingRoomsDatabase(
    source: MeetingRoomsDatabaseSource,
    driver: SQLiteDriver? = null,
): MeetingRoomsDatabase {
    return source.databaseBuilder()
        .setDriver(driver ?: BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

/** Room declares its builders per platform only, with no common declaration to call them through. */
internal expect fun MeetingRoomsDatabaseSource.databaseBuilder(): RoomDatabase.Builder<MeetingRoomsDatabase>
