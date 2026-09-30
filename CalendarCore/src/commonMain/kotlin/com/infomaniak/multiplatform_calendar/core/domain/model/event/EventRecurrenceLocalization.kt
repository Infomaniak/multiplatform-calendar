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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.toLocalizedString

/**
 * Describes this master event's RRULE using its DTSTART and start time zone.
 *
 * Only master events can be formatted reliably because materialized occurrences keep the master's
 * RRULE while their [EventTiming.start] points to the occurrence start rather than the master's DTSTART.
 * Using an occurrence start could therefore change the meaning of implicit RRULE selectors.
 *
 * Returns `null` for occurrences and when the event has no RRULE, including RDATE-only events.
 */
public suspend fun Event.toLocalizedRecurrenceString(): String? {
    if (isOccurrence) return null
    return timing.recurrenceRule?.toLocalizedString(
        start = timing.start,
        timeZone = timing.startTimeZone,
    )
}
