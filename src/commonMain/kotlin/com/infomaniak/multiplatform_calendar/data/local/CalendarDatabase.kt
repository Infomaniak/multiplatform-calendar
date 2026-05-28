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
package com.infomaniak.multiplatform_calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.infomaniak.multiplatform_calendar.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.data.local.dao.CalendarDao
import com.infomaniak.multiplatform_calendar.data.local.dao.EventDao
import com.infomaniak.multiplatform_calendar.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.data.local.entity.EventEntity

@Database(
    entities = [AccountEntity::class, CalendarEntity::class, EventEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CalendarTypeConverters::class)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
}

