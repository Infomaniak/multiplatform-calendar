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
package com.infomaniak.multiplatform_calendar.core.data.local

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * `AlarmEntity` is persisted as a JSON blob through [CalendarTypeConverters]. This exercises the
 * real Room converter path so a broken `Duration`/`Instant` serializer surfaces here instead of
 * at DB write time.
 */
class AlarmEntitySerializationTest {

    private val converters = CalendarTypeConverters()

    private fun roundTrip(alarm: AlarmEntity): AlarmEntity {
        val stored = converters.fromAlarms(listOf(alarm))
        return converters.toAlarms(stored).single()
    }

    @Test
    fun relativeTrigger_roundTrips_throughConverter() {
        val original = AlarmEntity(
            action = "DISPLAY",
            triggerRelative = (-15).minutes,
            triggerRelatedTo = TriggerRelation.End,
            description = "Reminder",
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun absoluteTrigger_roundTrips_throughConverter() {
        val original = AlarmEntity(
            action = "EMAIL",
            triggerAbsolute = Instant.fromEpochMilliseconds(1_781_514_000_000L),
            attachments = listOf("mailto:a@example.com"),
        )

        assertEquals(original, roundTrip(original))
    }
}
