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

package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule

import com.infomaniak.multiplatform_calendar.core.localization.RecurrenceTextFormatter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/**
 * Formats this RFC 5545 rule into a localized human-readable sentence.
 *
 * [start] is DTSTART. It is required because RFC 5545 derives omitted parts such as the weekday of
 * a WEEKLY rule or the day/month of a MONTHLY/YEARLY rule from DTSTART.
 *
 * [timeZone] is used only to present an UTC UNTIL value in the event's local calendar date. DATE
 * and floating UNTIL values keep their own RFC value domain.
 */
public suspend fun RecurrenceRule.toLocalizedString(
    start: LocalDateTime,
    timeZone: TimeZone? = null,
): String = RecurrenceTextFormatter().format(
    rule = this,
    start = start,
    timeZone = timeZone,
)
