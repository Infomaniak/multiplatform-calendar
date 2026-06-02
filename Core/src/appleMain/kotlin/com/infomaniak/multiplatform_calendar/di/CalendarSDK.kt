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
package com.infomaniak.multiplatform_calendar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.infomaniak.multiplatform_calendar.data.remote.caldav.di.CaldavClientModule
import com.infomaniak.multiplatform_calendar.core.AccountManager
import com.infomaniak.multiplatform_calendar.core.CalendarManager
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.di.CalendarCoreGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Dependency graph for Apple (iOS / macOS) consumers.
 *
 * Provides the Apple-specific [CalendarDatabase] binding. All other bindings
 * (CalendarSyncRemoteSource, DAOs, repositories, managers) are contributed automatically via
 * `@ContributesTo(AppScope)` modules (`CaldavClientModule`, `DatabaseModule`, `CalendarCoreGraph`).
 */
@DependencyGraph(scope = AppScope::class)
abstract class CalendarSDK : CalendarCoreGraph, CaldavClientModule {

    abstract override val accountManager: AccountManager
    abstract override val calendarManager: CalendarManager

    @OptIn(ExperimentalForeignApi::class)
    @SingleIn(AppScope::class)
    @Provides
    fun provideDatabase(): CalendarDatabase {
        val documentsPath = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )!!.path!!
        return Room.databaseBuilder<CalendarDatabase>(
            name = "$documentsPath/calendar.db",
        ).setDriver(BundledSQLiteDriver())
            .build()
    }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): CalendarSDK
    }
}

/**
 * Entry point for Apple consumers.
 *
 * Usage from Swift:
 * ```swift
 * let sdk = CalendarSDKProvider.shared.sdk
 * sdk.accountManager.initAccount(...)
 * sdk.calendarManager.observeCalendars(...)
 * ```
 */
object CalendarSDKProvider {
    val sdk: CalendarSDK by lazy {
        createGraphFactory<CalendarSDK.Factory>().create()
    }
}

