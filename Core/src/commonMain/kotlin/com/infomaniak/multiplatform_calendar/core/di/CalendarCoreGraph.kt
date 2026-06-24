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
package com.infomaniak.multiplatform_calendar.core.di

import com.infomaniak.multiplatform_calendar.core.managers.AccountManager
import com.infomaniak.multiplatform_calendar.core.managers.CalendarManager

/**
 * Public contract exposed by the Core dependency graph.
 *
 * It is implemented by the platform-specific Core graphs:
 * - **Android**: `AndroidCalendarGraph` (`@DependencyGraph(CalendarScope)`), obtained via
 *   `CalendarGraphProvider.create(context)`.
 * - **Apple**: `CalendarSDK` (`@DependencyGraph(CalendarScope)`), obtained via `CalendarSDKProvider.sdk(path)`.
 *
 * Host apps depend on **this** type only: it exposes the managers but hides every internal binding
 * (repositories, DAOs, the `:kmpdav` CalDAV client…). On Android the app graph receives an instance via
 * `@Includes` (graph dependency), so app code never needs to depend on `:kmpdav`.
 */
public interface CalendarCoreGraph {
    public val accountManager: AccountManager
    public val calendarManager: CalendarManager
}

