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

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalObjCRefinement::class)
public interface Event {
    @HiddenFromObjC
    public val id: EventId

    @HiddenFromObjC
    public val calendarId: CalendarId
    public val title: String
    public val description: String?
    public val location: String?
    public val status: String?
    public val categories: String?
    public val timing: EventTiming
    public val lastModified: Instant?
    public val attendees: List<Attendee>
    public val organizer: Attendee?
    @HiddenFromObjC
    public val color: CalendarColor
    public val colors: EventColors
    public val canEdit: Boolean
}
