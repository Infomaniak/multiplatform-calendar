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
package com.infomaniak.multiplatform_calendar.core.localization

import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal data class OverrideDomainWiringFixture(
    val eventWithOverrides: EventWithCalendarEntity,
    val occurrenceId: OccurrenceId.Recurrence,
    val rule: RecurrenceRule,
    val masterStart: LocalDateTime,
    val overrideStart: LocalDateTime,
)

internal fun overrideDomainWiringFixture(): OverrideDomainWiringFixture {
    val zone = TimeZone.UTC
    val masterStart = LocalDateTime(2026, 9, 30, 18, 30)
    val masterEnd = LocalDateTime(2026, 9, 30, 19, 30)
    val overriddenSlot = LocalDateTime(2026, 10, 30, 18, 30)

    // Deliberately move the override to another day so DTSTART differences are visible.
    val overrideStart = LocalDateTime(2026, 10, 2, 18, 30)
    val overrideEnd = LocalDateTime(2026, 10, 2, 19, 30)

    val rule = RecurrenceRule(freq = Frequency.Monthly)
    val masterId = EventId("event://override-localization")
    val calendarId = CalendarId("calendar://test")

    val master = EventEntity(
        id = masterId,
        calendarId = calendarId,
        content = EventContentEntity(
            summary = "Master",
            timing = EventTimingEntity(
                dtStart = masterStart,
                dtEndEffective = masterEnd,
                startTimeZone = zone.id,
                endTimeZone = zone.id,
                dtStartInstantMs = masterStart.toInstant(zone).toEpochMilliseconds(),
                dtEndInstantMs = masterEnd.toInstant(zone).toEpochMilliseconds(),
            ),
        ),
        rrule = rule,
        hasRecurrence = true,
        etag = "etag",
    )

    val recurrenceKey = RecurrenceKey.Utc(overriddenSlot.toInstant(zone))
    val override = EventOverrideEntity(
        masterId = masterId,
        recurrenceKey = recurrenceKey,
        recurrenceIdValue = "20261030T183000Z",
        recurrenceIdTzid = null,
        originalStartInstantMs = overriddenSlot.toInstant(zone).toEpochMilliseconds(),
        originalEndInstantMs = LocalDateTime(2026, 10, 30, 19, 30).toInstant(zone).toEpochMilliseconds(),
        originalStartLocalDateTime = overriddenSlot,
        originalEndLocalDateTime = LocalDateTime(2026, 10, 30, 19, 30),
        content = EventContentEntity(
            summary = "Moved override",
            timing = EventTimingEntity(
                dtStart = overrideStart,
                dtEndEffective = overrideEnd,
                startTimeZone = zone.id,
                endTimeZone = zone.id,
                dtStartInstantMs = overrideStart.toInstant(zone).toEpochMilliseconds(),
                dtEndInstantMs = overrideEnd.toInstant(zone).toEpochMilliseconds(),
            ),
        ),
    )

    return OverrideDomainWiringFixture(
        eventWithOverrides = EventWithCalendarEntity(
            event = master,
            calendar = CalendarEntity(
                id = calendarId,
                accountId = AccountId(1L),
                displayName = "Test",
                color = null,
            ),
            overrides = listOf(override),
        ),
        occurrenceId = OccurrenceId.Recurrence(masterId = masterId, recurrenceKey = recurrenceKey),
        rule = rule,
        masterStart = masterStart,
        overrideStart = overrideStart,
    )
}

