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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class EventAlarmIdTest {

    @Test
    fun serverUid_winsOverTheDerivedKey() {
        val alarm = alarm(uid = "valarm-uid-1")

        assertEquals(AlarmId.Uid("valarm-uid-1"), alarm.id)
        assertNotEquals(alarm(uid = null).id, alarm.id)
    }

    @Test
    fun withoutUid_theIdIsLocal_soItCannotBeMistakenForAServerUid() {
        // The type is the guarantee: only an `AlarmId.Uid` ever reaches the wire mapper.
        assertIs<AlarmId.Local>(alarm(uid = null).id)
    }

    @Test
    fun withoutUid_twoAlarmsFiringAlike_shareTheirId() {
        // Nothing tells them apart to the user either: they are the same reminder, entered twice.
        assertEquals(alarm(offset = (-15).minutes).id, alarm(offset = (-15).minutes).id)
    }

    @Test
    fun withoutUid_theSameOffsetWrittenTwoWays_yieldsTheSameId() {
        assertEquals(alarm(offset = (-15).minutes).id, alarm(offset = (-900).seconds).id)
    }

    @Test
    fun withoutUid_aDifferentOffset_yieldsADifferentId() {
        assertNotEquals(alarm(offset = (-15).minutes).id, alarm(offset = (-5).minutes).id)
    }

    @Test
    fun withoutUid_aDifferentAnchor_yieldsADifferentId() {
        val fromStart = alarm(offset = (-15).minutes, relatedTo = TriggerRelation.Start)
        val fromEnd = alarm(offset = (-15).minutes, relatedTo = TriggerRelation.End)

        assertNotEquals(fromStart.id, fromEnd.id)
    }

    @Test
    fun withoutUid_aDifferentAction_yieldsADifferentId() {
        assertNotEquals(alarm(action = AlarmAction.Display).id, alarm(action = AlarmAction.Audio).id)
    }

    @Test
    fun withoutUid_anAbsoluteTrigger_isToldApartFromARelativeOne() {
        val absolute = EventAlarm(
            action = AlarmAction.Display,
            trigger = AlarmTrigger.Absolute(Instant.fromEpochSeconds(1_781_514_000L)),
        )

        assertNotEquals(alarm().id, absolute.id)
        assertEquals(absolute.id, absolute.copy().id)
    }

    private fun alarm(
        uid: String? = null,
        offset: Duration = (-15).minutes,
        relatedTo: TriggerRelation = TriggerRelation.Start,
        action: AlarmAction = AlarmAction.Display,
    ) = EventAlarm(
        action = action,
        trigger = AlarmTrigger.Relative(offset = offset, relatedTo = relatedTo),
        uid = uid?.let(AlarmId::Uid),
    )
}
