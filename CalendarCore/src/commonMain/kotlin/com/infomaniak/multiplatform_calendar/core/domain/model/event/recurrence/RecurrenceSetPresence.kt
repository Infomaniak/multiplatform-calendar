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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule

/**
 * Single source of truth for what makes an event a series: a rule, dates listed one by one, or both.
 *
 * `EXDATE` is deliberately absent. It only removes occurrences from a set the other two produce, so an
 * event carrying nothing but exception dates stays a plain event.
 */
internal fun hasRecurrenceSet(
    recurrenceRule: RecurrenceRule?,
    rDates: List<IcalDateValue>,
): Boolean = recurrenceRule != null || rDates.isNotEmpty()

/** The [hasRecurrenceSet] test applied to an already assembled [EventTiming]. */
internal fun EventTiming.hasRecurrenceSet(): Boolean = hasRecurrenceSet(recurrenceRule, rDates)
