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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTimeRange
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Build the persisted [EventTimingEntity] from an edited domain [EventTimeRange].
 *
 * The edited timing always carries an explicit end, so any pre-existing `DURATION` is dropped
 * (RFC 5545 §3.8.2.5: `DTEND` and `DURATION` are mutually exclusive) and [EventTimingEntity.dtEndEffective]
 * is simply [EventTimeRange.end]. Precise epoch-ms values are copied directly rather than resolved
 * from their wall-clock again; floating events keep a `null` instant.
 */
internal fun EventTimeRange.toEntity(): EventTimingEntity {
    val startLocal = startLocalDateTime
    val endLocal = endLocalDateTime
    return EventTimingEntity(
        dtStart = startLocal,
        dtEnd = endLocal,
        duration = null,
        dtEndEffective = endLocal,
        startTimeZone = startTimeZone?.id,
        endTimeZone = endTimeZone?.id,
        dtStartInstantMs = start.storageInstant(isAllDay),
        dtEndInstantMs = end.storageInstant(isAllDay),
        isAllDay = isAllDay,
    )
}

/**
 * Preserve the already-resolved instant of a precise value. All-day floating values retain their
 * UTC-midnight storage anchor for device-independent range indexing.
 */
private fun EventTiming.storageInstant(isAllDay: Boolean): Long? = when (this) {
    is EventTiming.Precised -> instant.toEpochMilliseconds()
    is EventTiming.Floating -> if (isAllDay) {
        localDateTime.toInstant(TimeZone.UTC).toEpochMilliseconds()
    } else {
        null
    }
}
