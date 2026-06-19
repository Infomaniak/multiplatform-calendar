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

import android.content.Context
import com.infomaniak.multiplatform_calendar.core.di.CalendarCoreGraph
import com.infomaniak.multiplatform_calendar.core.di.SdkScope
import com.infomaniak.multiplatform_calendar.data.remote.caldav.di.CaldavClientModule
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * Self-contained Core dependency graph for Android consumers — the counterpart of Apple's `CalendarSDK`.
 *
 * It owns the [SdkScope] and wires everything internally (managers, repositories, Room database,
 * and the `:kmpdav` CalDAV client via [CaldavClientModule]). Because it is compiled here in `:Core`
 * (which depends on `:kmpdav` as `implementation`), none of those internal bindings — nor `:kmpdav` —
 * leak to the host app.
 *
 * The host app obtains it through [CalendarSdkGraphProvider.create], which only exposes [CalendarCoreGraph].
 */
@DependencyGraph(scope = SdkScope::class)
internal interface CalendarSdkGraph : CalendarCoreGraph, CaldavClientModule {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides appContext: Context): CalendarSdkGraph
    }
}

/**
 * Entry point for the Android host app.
 *
 * Returns a [CalendarCoreGraph] (managers only): the concrete graph type and every internal binding
 * stay hidden, so the app neither sees nor depends on `:kmpdav`.
 */
public object CalendarSdkGraphProvider {

    public fun create(appContext: Context): CalendarCoreGraph {
        return createGraphFactory<CalendarSdkGraph.Factory>().create(appContext)
    }
}

