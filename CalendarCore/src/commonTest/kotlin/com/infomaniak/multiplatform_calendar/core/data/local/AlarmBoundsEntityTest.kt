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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmBoundsEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AlarmBoundsEntityTest {

    @Test
    fun noAlarm_leavesEveryBoundNull() {
        val bounds = AlarmBoundsEntity.of(alarms = emptyList(), timing = utcTiming())

        assertEquals(AlarmBoundsEntity(), bounds, "all-null is what tells a row apart as alarm-free")
    }

    @Test
    fun relativeTriggers_spanTheSmallestAndLargestOffset() {
        val bounds = AlarmBoundsEntity.of(
            alarms = listOf(
                display(triggerRelative = (-15).minutes),
                display(triggerRelative = (-40).days),
                display(triggerRelative = 5.minutes),
            ),
            timing = utcTiming(),
        )

        assertEquals((-40).days.inWholeMilliseconds, bounds.minAlarmOffsetMs)
        assertEquals(5.minutes.inWholeMilliseconds, bounds.maxAlarmOffsetMs)
        assertNull(bounds.minAbsoluteAlarmMs)
    }

    @Test
    fun endRelatedTrigger_isCountedFromTheStartInstead() {
        val bounds = AlarmBoundsEntity.of(
            alarms = listOf(display(triggerRelative = (-10).minutes, relatedTo = TriggerRelation.End)),
            timing = utcTiming(),
        )

        assertEquals(
            (2.hours - 10.minutes).inWholeMilliseconds,
            bounds.minAlarmOffsetMs,
            "the reminder trails a two-hour event, so it goes off well after it started",
        )
    }

    @Test
    fun endRelatedTrigger_onAFloatingEvent_padsItsWallClockDurationForClockChanges() {
        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        val floating = EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = LocalDateTime(2026, 6, 15, 12, 0),
            dtStartInstantMs = null,
            dtEndInstantMs = null,
        )

        val bounds = AlarmBoundsEntity.of(
            alarms = listOf(display(triggerRelative = (-10).minutes, relatedTo = TriggerRelation.End)),
            timing = floating,
        )

        val fromStart = 2.hours - 10.minutes
        assertEquals(
            (fromStart - 2.hours).inWholeMilliseconds,
            bounds.minAlarmOffsetMs,
            "a clock change can make the event shorter than its wall-clock reads, firing the reminder earlier",
        )
        assertEquals((fromStart + 2.hours).inWholeMilliseconds, bounds.maxAlarmOffsetMs)
    }

    /** `DTEND` may name another zone than `DTSTART` (RFC 5545 §3.8.2.2), which only the instants resolve. */
    @Test
    fun endRelatedTrigger_onACrossZoneEvent_readsItsAbsoluteDuration() {
        val dtStart = LocalDateTime(2026, 6, 15, 9, 0)
        val dtEnd = LocalDateTime(2026, 6, 15, 16, 0)
        val flight = EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = dtEnd,
            startTimeZone = "America/New_York",
            endTimeZone = "Europe/Paris",
            dtStartInstantMs = dtStart.toInstant(TimeZone.of("America/New_York")).toEpochMilliseconds(),
            dtEndInstantMs = dtEnd.toInstant(TimeZone.of("Europe/Paris")).toEpochMilliseconds(),
        )

        val bounds = AlarmBoundsEntity.of(
            alarms = listOf(display(triggerRelative = (-10).minutes, relatedTo = TriggerRelation.End)),
            timing = flight,
        )

        assertEquals(
            (1.hours - 10.minutes).inWholeMilliseconds,
            bounds.minAlarmOffsetMs,
            "the flight lasts an hour, not the seven its wall-clocks read apart",
        )
    }

    @Test
    fun absoluteTriggers_areKeptApartFromTheRelativeOnes() {
        val early = LocalDateTime(2026, 1, 1, 8, 0).toInstant(TimeZone.UTC)
        val late = LocalDateTime(2026, 3, 1, 8, 0).toInstant(TimeZone.UTC)

        val bounds = AlarmBoundsEntity.of(
            alarms = listOf(
                display(triggerAbsolute = late),
                display(triggerAbsolute = early),
                display(triggerRelative = (-15).minutes),
            ),
            timing = utcTiming(),
        )

        assertEquals(early.toEpochMilliseconds(), bounds.minAbsoluteAlarmMs)
        assertEquals(late.toEpochMilliseconds(), bounds.maxAbsoluteAlarmMs)
        assertEquals((-15).minutes.inWholeMilliseconds, bounds.minAlarmOffsetMs, "a fixed instant never shifts a window")
    }

    @Test
    fun triggerlessAlarm_isSkipped() {
        val bounds = AlarmBoundsEntity.of(alarms = listOf(display()), timing = utcTiming())

        assertEquals(AlarmBoundsEntity(), bounds, "the projection ignores it too, having nothing to fire on")
    }

    @Test
    fun alarmCarryingBothTriggers_countsAsRelativeOnly() {
        val bounds = AlarmBoundsEntity.of(
            alarms = listOf(
                display(
                    triggerRelative = (-15).minutes,
                    triggerAbsolute = LocalDateTime(2026, 1, 1, 8, 0).toInstant(TimeZone.UTC),
                ),
            ),
            timing = utcTiming(),
        )

        assertEquals((-15).minutes.inWholeMilliseconds, bounds.minAlarmOffsetMs)
        assertNull(bounds.minAbsoluteAlarmMs, "the projection reads the relative trigger and ignores the other")
    }

    private fun display(
        triggerRelative: kotlin.time.Duration? = null,
        triggerAbsolute: Instant? = null,
        relatedTo: TriggerRelation = TriggerRelation.Start,
    ) = AlarmEntity(
        action = "DISPLAY",
        triggerRelative = triggerRelative,
        triggerAbsolute = triggerAbsolute,
        triggerRelatedTo = relatedTo,
    )

    /** A two-hour event, so an end-related trigger reads differently than a start-related one. */
    private fun utcTiming(): EventTimingEntity {
        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        val dtEnd = LocalDateTime(2026, 6, 15, 12, 0)
        return EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = dtEnd,
            startTimeZone = TimeZone.UTC.id,
            endTimeZone = TimeZone.UTC.id,
            dtStartInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            dtEndInstantMs = dtEnd.toInstant(TimeZone.UTC).toEpochMilliseconds(),
        )
    }
}
