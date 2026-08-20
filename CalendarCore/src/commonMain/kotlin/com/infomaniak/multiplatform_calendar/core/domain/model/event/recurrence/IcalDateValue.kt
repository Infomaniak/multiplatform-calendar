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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Typed iCalendar date values used by `RDATE` / `EXDATE`. */
@Serializable
public sealed interface IcalDateValue {
    // Kept for future write-path re-encoding where original zone identifiers must round-trip.
    @Serializable
    public data class Zoned(val instant: Instant, val timeZoneId: String) : IcalDateValue

    @Serializable
    public data class AllDay(val date: LocalDate) : IcalDateValue

    @Serializable
    public data class Floating(val localDateTime: LocalDateTime) : IcalDateValue
}
