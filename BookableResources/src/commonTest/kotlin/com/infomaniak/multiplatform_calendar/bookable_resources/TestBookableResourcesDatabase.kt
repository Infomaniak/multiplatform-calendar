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
package com.infomaniak.multiplatform_calendar.bookable_resources

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.BookableResourcesDatabase
import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.BookableResourceEntity

@Database(entities = [BookableResourceEntity::class], version = 1, exportSchema = false)
@ConstructedBy(TestBookableResourcesDatabaseConstructor::class)
internal abstract class TestBookableResourcesDatabase : RoomDatabase(), BookableResourcesDatabase

@Suppress("KotlinNoActualForExpect")
internal expect object TestBookableResourcesDatabaseConstructor : RoomDatabaseConstructor<TestBookableResourcesDatabase> {
    override fun initialize(): TestBookableResourcesDatabase
}
