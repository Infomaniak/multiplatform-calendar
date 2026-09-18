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

/**
 * This override, carrying the fields a series edit changed between [before] and [after].
 *
 * iCalendar has no inheritance: an override is a VEVENT of its own, complete enough to repeat the
 * master's `SUMMARY` (RFC 4791 §4.1). A field the series changes therefore reaches it only by being
 * written again, and one the edit left alone must stay as the override has it — hence carrying the
 * *difference* rather than the whole of [after].
 *
 * Returns `null` when nothing the override holds is affected, leaving it untouched.
 *
 * Timing and recurrence are left out: moving the series moves the slots `RECURRENCE-ID` addresses,
 * which is a question of its own. [calendarId] too — an override has no resource to move.
 */
internal fun EventEditData.withSeriesChanges(before: EventEditData, after: EventEditData): EventEditData? {
    var carried = this
    if (after.title != before.title) carried = carried.copy(title = after.title)
    if (after.location != before.location) carried = carried.copy(location = after.location)
    if (after.description != before.description) carried = carried.copy(description = after.description)
    if (after.timeBlocking != before.timeBlocking) carried = carried.copy(timeBlocking = after.timeBlocking)
    if (after.eventColor != before.eventColor) carried = carried.copy(eventColor = after.eventColor)
    if (after.alarms != before.alarms) carried = carried.copy(alarms = after.alarms)

    return carried.takeIf { it != this }
}
