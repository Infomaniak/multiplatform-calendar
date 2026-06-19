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

/**
 * DI scope owned by the **Core** dependency graph (`AndroidCalendarGraph` on Android,
 * `CalendarSDK` on Apple).
 *
 * Everything wired here — managers, repositories, DAOs, the CalDAV client (`:kmpdav`) — lives
 * inside the Core graph and is **not** re-exposed to consumers. Host apps depend on the Core graph
 * through [CalendarCoreGraph] (which only exposes the managers), so they never need to see the
 * internal bindings nor depend on `:kmpdav`.
 */
public abstract class SdkScope private constructor()

