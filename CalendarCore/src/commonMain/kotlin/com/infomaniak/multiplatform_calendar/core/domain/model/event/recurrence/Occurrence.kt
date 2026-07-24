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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/**
 * A single materialised recurrence instance produced by
 * [RecurrenceExpander][com.infomaniak.multiplatform_calendar.core.domain.recurrence.RecurrenceExpander].
 *
 * The timing fields mirror
 * [EventTiming][com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming] exactly
 * (same four value-type forms, [end] exclusive, wall-clock preserved across DST) so an occurrence
 * can be turned into a concrete `Event` without re-deriving its shape. [key] is the occurrence's
 * stable identity, faithful to the master `DTSTART` value type; [isAllDay] is read straight from it
 * since an all-day identity is the only all-day form.
 */
internal data class Occurrence(
    val key: RecurrenceKey,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val startTimeZone: TimeZone?,
    val endTimeZone: TimeZone?,
) {
    /** An all-day identity ([RecurrenceKey.AllDay]) is the only all-day form, so it fully defines this. */
    val isAllDay: Boolean get() = key is RecurrenceKey.AllDay
}
