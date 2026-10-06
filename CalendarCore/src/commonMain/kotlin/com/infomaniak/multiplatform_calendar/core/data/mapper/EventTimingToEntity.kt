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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventBounds
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.endWallClock
import com.infomaniak.multiplatform_calendar.core.domain.model.event.startWallClock
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Build the persisted [EventTimingEntity] from an edited domain [EventTiming].
 *
 * The edited timing always carries an explicit end, so any pre-existing `DURATION` is dropped
 * (RFC 5545 §3.8.2.5: `DTEND` and `DURATION` are mutually exclusive) and [EventTimingEntity.dtEndEffective]
 * is simply the end wall-clock. Epoch-ms columns hold [storedStartInstant] / [storedEndInstant].
 */
internal fun EventTiming.toEntity(): EventTimingEntity {
    val zoned = bounds as? EventBounds.Zoned
    return EventTimingEntity(
        dtStart = bounds.startWallClock,
        dtEnd = bounds.endWallClock,
        duration = null,
        dtEndEffective = bounds.endWallClock,
        startTimeZone = zoned?.start?.timeZone?.id,
        endTimeZone = zoned?.end?.timeZone?.id,
        dtStartInstantMs = bounds.storedStartInstant()?.toEpochMilliseconds(),
        dtEndInstantMs = bounds.storedEndInstant()?.toEpochMilliseconds(),
        isAllDay = isAllDay,
    )
}

/**
 * The start recorded in the epoch-ms columns, or `null` for floating events (RFC 5545 FORM #1) which have no absolute
 * instant by definition. See [EventTimingEntity.dtStartInstantMs] for the DAO's wall-clock fallback branch on `null`.
 * An all-day start is anchored in `TimeZone.UTC` so the recorded epoch ms is device-independent.
 */
private fun EventBounds.storedStartInstant(): Instant? = when (this) {
    is EventBounds.AllDay -> startInstant(TimeZone.UTC)
    is EventBounds.Floating -> null
    is EventBounds.Zoned -> start.instant
}

/** See [storedStartInstant]. */
private fun EventBounds.storedEndInstant(): Instant? = when (this) {
    is EventBounds.AllDay -> endInstant(TimeZone.UTC)
    is EventBounds.Floating -> null
    is EventBounds.Zoned -> end.instant
}
