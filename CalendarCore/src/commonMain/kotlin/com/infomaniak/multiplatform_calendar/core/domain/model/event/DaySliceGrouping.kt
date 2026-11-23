/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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

import kotlinx.datetime.DayOfWeek

/** Which multi-day events are kept as one [EventDaySlice] spanning several days instead of one slice per day. */
public sealed interface DaySliceGrouping {

    public data object None : DaySliceGrouping

    public data object AllDay : DaySliceGrouping

    /** Every multi-day event, all-day or timed, split at each [weekStart]. */
    public data class MultiDay(val weekStart: DayOfWeek) : DaySliceGrouping
}
