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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LocalAlarmIdTest {

    @Test
    fun twoAlarmsFiringAlike_shareTheirId() {
        assertEquals(localId(offset = (-15).minutes), localId(offset = (-15).minutes))
    }

    @Test
    fun theSameOffsetWrittenTwoWays_yieldsTheSameId() {
        assertEquals(localId(offset = (-15).minutes), localId(offset = (-900).seconds))
    }

    @Test
    fun aDifferentOffset_yieldsADifferentId() {
        assertNotEquals(localId(offset = (-15).minutes), localId(offset = (-5).minutes))
    }

    @Test
    fun aDifferentAnchor_yieldsADifferentId() {
        assertNotEquals(localId(relatedTo = TriggerRelation.Start), localId(relatedTo = TriggerRelation.End))
    }

    @Test
    fun aDifferentAction_yieldsADifferentId() {
        assertNotEquals(localId(action = AlarmAction.Display), localId(action = AlarmAction.Audio))
    }

    @Test
    fun anAbsoluteTrigger_isToldApartFromARelativeOne() {
        val absolute = localAlarmId(AlarmAction.Display, AlarmTrigger.Absolute(Instant.fromEpochSeconds(1_781_514_000L)))

        assertNotEquals(localId(), absolute)
    }

    private fun localId(
        offset: Duration = (-15).minutes,
        relatedTo: TriggerRelation = TriggerRelation.Start,
        action: AlarmAction = AlarmAction.Display,
    ) = localAlarmId(action, AlarmTrigger.Relative(offset = offset, relatedTo = relatedTo))
}
