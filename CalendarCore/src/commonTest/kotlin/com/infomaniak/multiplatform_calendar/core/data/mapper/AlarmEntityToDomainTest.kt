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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmTrigger
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AlarmEntityToDomainTest {

    @Test
    fun relativeTrigger_withEnd_producesRelativeAlarmWithEndAnchor() {
        val domain = AlarmEntity(
            action = "DISPLAY",
            triggerRelative = (-10).minutes,
            triggerRelatedTo = TriggerRelation.End,
        ).toDomain()!!

        val trigger = assertIs<AlarmTrigger.Relative>(domain.trigger)
        assertEquals((-10).minutes, trigger.offset)
        assertEquals(TriggerRelation.End, trigger.relatedTo)
    }

    @Test
    fun absoluteTrigger_producesAbsoluteAlarm() {
        val instant = Instant.fromEpochMilliseconds(1_781_514_000_000L)
        val domain = AlarmEntity(
            action = "DISPLAY",
            triggerAbsolute = instant,
            triggerRelatedTo = TriggerRelation.Start,
        ).toDomain()!!

        val trigger = assertIs<AlarmTrigger.Absolute>(domain.trigger)
        assertEquals(instant, trigger.instant)
    }

    @Test
    fun unknownAction_isSurfacedAsUnknownWithOriginalValue() {
        val domain = AlarmEntity(
            action = "PROCEDURE",
            triggerRelative = 0L.milliseconds,
            triggerRelatedTo = TriggerRelation.Start,
        ).toDomain()!!

        assertEquals(AlarmAction.Unknown("PROCEDURE"), domain.action)
    }

    @Test
    fun bothTriggersNull_dropsAlarm() {
        val domain = AlarmEntity(
            action = "DISPLAY",
            triggerRelative = null,
            triggerAbsolute = null,
            triggerRelatedTo = TriggerRelation.Start,
        ).toDomain()

        assertNull(domain)
    }
}
