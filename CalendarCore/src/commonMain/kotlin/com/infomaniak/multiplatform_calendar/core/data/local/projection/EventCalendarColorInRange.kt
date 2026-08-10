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
package com.infomaniak.multiplatform_calendar.core.data.local.projection

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.LocalDateTime

/**
 * Lightweight Room projection: just what is needed to place an event's **calendar** color on each day
 * it covers, without materialising a full `EventEntity` (no title, attendees, raw ICS, …).
 *
 * The wall-clock columns plus their IANA time-zone ids mirror the domain `EventTiming.startIn` /
 * `EventTiming.endIn` reprojection so day placement matches the planning grid exactly:
 * - [startZoneId]/[endZoneId] `== null` (floating or all-day): the wall-clock is used as-is in the display zone.
 * - otherwise: the wall-clock is reprojected through an absolute instant into the display zone.
 *
 * [colorArgb] is the owning calendar's source color (`calendars.color`), fed to `CalendarColors.from` to derive the
 * full `CalendarColors`; `null` means "use the default color". [rrule] carries recurring masters so callers can
 * expand occurrences without materialising full event objects.
 */
internal data class EventCalendarColorInRange(
    val eventId: EventId,
    val calendarId: CalendarId,
    val colorArgb: Int?,
    val dtStart: LocalDateTime,
    val dtEndEffective: LocalDateTime,
    val startZoneId: String?,
    val endZoneId: String?,
    val isAllDay: Boolean,
    val rrule: RecurrenceRule?,
    val rDates: List<IcalDateValue>,
    val exDates: List<IcalDateValue>,
)


