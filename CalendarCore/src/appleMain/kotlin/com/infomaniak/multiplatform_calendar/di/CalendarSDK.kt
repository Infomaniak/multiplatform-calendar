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

import com.infomaniak.multiplatform_calendar.core.data.CrashReport
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.DatabaseConfig
import com.infomaniak.multiplatform_calendar.core.di.CalendarCoreGraph
import com.infomaniak.multiplatform_calendar.data.remote.caldav.di.CaldavClientModule
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * Dependency graph for Apple (iOS / macOS) consumers.
 *
 * Provides the Apple-specific [CalendarDatabase] binding. All other bindings
 * (CalendarSyncRemoteSource, DAOs, repositories, managers) are contributed automatically via
 * `@ContributesTo(AppScope)` modules (`CaldavClientModule`, `DatabaseModule`, `CalendarCoreGraph`).
 */
@DependencyGraph(scope = AppScope::class)
internal abstract class CalendarSDK internal constructor() : CalendarCoreGraph, CaldavClientModule {

    @DependencyGraph.Factory
    internal fun interface Factory {
        fun create(
            @Provides databaseConfig: DatabaseConfig,
            @Provides crashReport: CrashReport,
        ): CalendarSDK
    }
}

/**
 * Entry point for Apple consumers.
 *
 * Usage from Swift:
 * ```swift
 * let sdk = CalendarSDKProvider.shared.sdk(databasePath: "/path/to/calendar.db")
 * sdk.accountManager.initAccount(...)
 * sdk.calendarManager.observeCalendars(...)
 * ```
 */
public object CalendarSDKProvider {

    public fun sdk(databasePath: String, crashReport: CrashReport): CalendarCoreGraph {
        return createGraphFactory<CalendarSDK.Factory>().create(DatabaseConfig(path = databasePath), crashReport)
    }
}
