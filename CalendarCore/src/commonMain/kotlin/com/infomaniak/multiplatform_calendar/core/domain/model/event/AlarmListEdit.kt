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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

/** Where an edit's `VALARM`s come from, mirroring [DateListEdit] for the alarm list. */
internal enum class AlarmListEdit {

    /** Take them from the edit, emitting a replacement for whatever differs from the stored ones. */
    FromData,

    /**
     * Leave the resource's own `VALARM`s alone.
     *
     * For an operation that edits the recurrence set rather than the event: the alarms an edit carries
     * are a *domain* projection, which drops any stored alarm whose trigger cannot be read, and the
     * replacement that gap provokes would delete it from the resource.
     */
    Preserve,
}
