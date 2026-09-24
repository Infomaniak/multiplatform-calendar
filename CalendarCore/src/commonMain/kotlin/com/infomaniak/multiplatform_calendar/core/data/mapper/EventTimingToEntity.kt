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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.endTimeZone
import com.infomaniak.multiplatform_calendar.core.domain.model.event.endWallClock
import com.infomaniak.multiplatform_calendar.core.domain.model.event.startTimeZone
import com.infomaniak.multiplatform_calendar.core.domain.model.event.startWallClock
import kotlinx.datetime.TimeZone

/**
 * Build the persisted [EventTimingEntity] from an edited domain [EventTiming].
 *
 * The edited timing always carries an explicit end, so any pre-existing `DURATION` is dropped
 * (RFC 5545 §3.8.2.5: `DTEND` and `DURATION` are mutually exclusive) and [EventTimingEntity.dtEndEffective]
 * is simply the wall-clock of [EventTiming.end]. Epoch-ms columns are anchored via [startStorageZone] / [endStorageZone]
 * (`null` for floating events — see [EventTimingEntity.dtStartInstantMs]).
 */
internal fun EventTiming.toEntity(): EventTimingEntity {
    val startZone = startStorageZone()
    val endZone = endStorageZone()
    return EventTimingEntity(
        dtStart = startWallClock,
        dtEnd = endWallClock,
        duration = null,
        dtEndEffective = endWallClock,
        startTimeZone = startTimeZone?.id,
        endTimeZone = endTimeZone?.id,
        dtStartInstantMs = startZone?.let { startInstant(it).toEpochMilliseconds() },
        dtEndInstantMs = endZone?.let { endInstant(it).toEpochMilliseconds() },
        isAllDay = isAllDay,
    )
}

/**
 * Time-zone in which to resolve [EventTiming.start] for storage (epoch-ms columns), or `null` for
 * floating events (RFC 5545 FORM #1) which have no absolute instant by definition. See
 * [EventTimingEntity.dtStartInstantMs] for the DAO's wall-clock fallback branch on `null`.
 *
 * - All-day → `TimeZone.UTC` so the recorded epoch ms is device-independent.
 * - Zoned   → the zone of [EventTiming.start].
 * - Floating (no `TZID`, no `Z`) → `null`.
 */
private fun EventTiming.startStorageZone(): TimeZone? = storageZoneFor(startTimeZone)

/** See [startStorageZone]. Uses the zone of [EventTiming.end] (which can differ from the start one). */
private fun EventTiming.endStorageZone(): TimeZone? = storageZoneFor(endTimeZone)

private fun EventTiming.storageZoneFor(zone: TimeZone?): TimeZone? = when {
    isAllDay -> TimeZone.UTC
    else -> zone
}
