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

import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class UpcomingAlarmProjectionTest {

    @Test
    fun relativeTrigger_firesAnOffsetBeforeTheEventStarts() {
        val event = eventAt(day = 10, alarms = listOf(reminder(15.minutes)))

        val alarms = listOf(event).project()

        assertEquals(1, alarms.size)
        assertEquals(utc(day = 10, hour = 9, minute = 45), alarms.single().firesAt)
    }

    @Test
    fun relativeTrigger_anchoredToTheEnd_firesFromTheEndOfTheEvent() {
        val alarm = EventAlarm(AlarmAction.Display, AlarmTrigger.Relative(-5.minutes, TriggerRelation.End))
        val event = eventAt(day = 10, alarms = listOf(alarm))

        val alarms = listOf(event).project()

        assertEquals(utc(day = 10, hour = 10, minute = 55), alarms.single().firesAt)
    }

    @Test
    fun relativeTrigger_onARecurringEvent_firesOncePerOccurrence() {
        val master = eventAt(day = 10, alarms = listOf(reminder(15.minutes)))
        val occurrences = listOf(master.occurrenceOn(day = 10), master.occurrenceOn(day = 11))

        val alarms = occurrences.project()

        assertEquals(
            listOf(utc(day = 10, hour = 9, minute = 45), utc(day = 11, hour = 9, minute = 45)),
            alarms.map(UpcomingAlarm::firesAt),
        )
        assertEquals(2, alarms.map(UpcomingAlarm::id).toSet().size, "each occurrence gets an identity of its own")
    }

    @Test
    fun absoluteTrigger_onARecurringEvent_firesOnceForTheWholeSeries() {
        val alarm = EventAlarm(AlarmAction.Display, AlarmTrigger.Absolute(utc(day = 9, hour = 8)))
        val master = eventAt(day = 10, alarms = listOf(alarm))
        val occurrences = listOf(
            master.occurrenceOn(day = 10),
            master.occurrenceOn(day = 11),
            master.occurrenceOn(day = 12),
        )

        val alarms = occurrences.project()

        assertEquals(
            1,
            alarms.size,
            "RFC 5545 §3.8.6.3: an absolute trigger names one point in time, not one per occurrence",
        )
        assertEquals(utc(day = 9, hour = 8), alarms.single().firesAt)
    }

    @Test
    fun movingAnEvent_changesTheIdOfItsAlarms() {
        val master = eventAt(day = 10, alarms = listOf(reminder(15.minutes)))
        val slot = RecurrenceKey.Utc(utc(day = 11, hour = 10))
        val onTime = master.occurrenceOn(day = 11)
        // Same slot, hence the same occurrenceId, but the occurrence itself was moved four hours later.
        val moved = onTime.copy(
            timing = onTime.timing.copy(start = at(day = 11, hour = 14), end = at(day = 11, hour = 15)),
        )

        assertEquals(onTime.occurrenceId, moved.occurrenceId, "moving an occurrence leaves its RECURRENCE-ID alone")
        assertEquals(OccurrenceId.Recurrence(master.masterEventId, slot), moved.occurrenceId)
        assertNotEquals(
            listOf(onTime).project().single().id,
            listOf(moved).project().single().id,
            "a client diffing ids must see the reminder move, or it would leave one standing at the old time",
        )
    }

    @Test
    fun twoAlarmsColliding_areStillToldApart() {
        val event = eventAt(day = 10, alarms = listOf(reminder(15.minutes), reminder(15.minutes)))

        val alarms = listOf(event).project()

        assertEquals(2, alarms.size)
        assertEquals(2, alarms.map(UpcomingAlarm::id).toSet().size)
    }

    @Test
    fun twoCollidingAlarms_areToldApartByTheirServerUid() {
        // Same firing, same action: only the UID the server gave each one sets them apart.
        val event = eventAt(
            day = 10,
            alarms = listOf(reminder(15.minutes, uid = "valarm-a"), reminder(15.minutes, uid = "valarm-b")),
        )

        val ids = listOf(event).project().map(UpcomingAlarm::id).map(UpcomingAlarmId::value)

        assertEquals(2, ids.toSet().size)
        assertTrue(ids.any { it.contains("|valarm-a|") }, "expected an id built on the first UID, got $ids")
        assertTrue(ids.any { it.contains("|valarm-b|") }, "expected an id built on the second UID, got $ids")
    }

    @Test
    fun twoEventsFiringAtTheSameMoment_areToldApart() {
        // Two unrelated events whose reminders land on the very same second, 09:45.
        val meeting = eventAt(day = 10, alarms = listOf(reminder(15.minutes)))
        val lunch = eventAt(day = 11, alarms = listOf(reminder(24.hours + 15.minutes)))

        val alarms = listOf(meeting, lunch).project()

        assertEquals(
            listOf(utc(day = 10, hour = 9, minute = 45), utc(day = 10, hour = 9, minute = 45)),
            alarms.map(UpcomingAlarm::firesAt),
            "the fixture is only worth anything if both really collide",
        )
        assertEquals(
            2,
            alarms.map(UpcomingAlarm::id).toSet().size,
            "an id opens on the event it belongs to, so a collision across events is not one",
        )
    }

    @Test
    fun reorderingUnrelatedAlarms_leavesTheirIdsAlone() {
        val alarms = listOf(reminder(15.minutes), reminder(1.hours), reminder(2.days))
        val asIs = listOf(eventAt(day = 10, alarms = alarms)).project()
        val shuffled = listOf(eventAt(day = 10, alarms = alarms.reversed())).project()

        assertEquals(
            asIs.map(UpcomingAlarm::id).toSet(),
            shuffled.map(UpcomingAlarm::id).toSet(),
            "ids count per key, not along the list, so reordering must not reschedule anything",
        )
    }

    @Test
    fun filteringOutAnAction_leavesTheIdsOfTheOthersAlone() {
        val email = EventAlarm(AlarmAction.Email, AlarmTrigger.Relative(-15.minutes, TriggerRelation.Start))
        val event = eventAt(day = 10, alarms = listOf(email, reminder(15.minutes)))

        val withEmail = listOf(event).project(actions = setOf(AlarmAction.Display, AlarmAction.Email))
        val withoutEmail = listOf(event).project(actions = setOf(AlarmAction.Display))

        assertEquals(2, withEmail.size)
        assertEquals(1, withoutEmail.size)
        assertEquals(
            withEmail.single { it.alarm.action == AlarmAction.Display }.id,
            withoutEmail.single().id,
            "the action is part of the key, so dropping one never renames the others",
        )
    }

    @Test
    fun alarmsOutsideTheWindow_areLeftOut() {
        val soon = eventAt(day = 10, alarms = listOf(reminder(15.minutes)))
        val tooLate = eventAt(day = 25, alarms = listOf(reminder(15.minutes)))
        val alreadyPast = eventAt(day = 1, alarms = listOf(reminder(15.minutes)))

        val alarms = listOf(tooLate, soon, alreadyPast).project(
            from = utc(day = 5, hour = 0),
            until = utc(day = 20, hour = 0),
        )

        assertEquals(1, alarms.size)
        assertEquals(utc(day = 10, hour = 9, minute = 45), alarms.single().firesAt)
    }

    @Test
    fun theSoonestAlarmsComeFirst_andOnlyLimitOfThem() {
        val events = listOf(
            eventAt(day = 12, alarms = listOf(reminder(Duration.ZERO))),
            eventAt(day = 10, alarms = listOf(reminder(Duration.ZERO))),
            eventAt(day = 11, alarms = listOf(reminder(Duration.ZERO))),
        )

        val alarms = events.project(limit = 2)

        assertEquals(listOf(utc(day = 10, hour = 10), utc(day = 11, hour = 10)), alarms.map(UpcomingAlarm::firesAt))
    }

    @Test
    fun anAlarmCarriesTheEventItBelongsTo() {
        val event = eventAt(day = 10, alarms = listOf(reminder(15.minutes)))

        val alarm = listOf(event).project().single()

        assertEquals(event, alarm.event)
        assertEquals(event.alarms.single(), alarm.alarm)
        assertTrue(alarm.id.value.isNotEmpty())
    }

    @Test
    fun noTwoUpcomingAlarms_shareAnId_andNoneGoesMissing() {
        // The contract the front schedules on: one notification per alarm, never two under one id.
        val crowded = eventAt(
            day = 10,
            alarms = listOf(
                reminder(15.minutes),                   // a plain duplicate pair, told apart by ordinal
                reminder(15.minutes),
                reminder(30.minutes, uid = "valarm-a"), // a second pair, told apart by their server UID
                reminder(30.minutes, uid = "valarm-b"),
            ),
        )
        val series = eventAt(
            day = 20,
            alarms = listOf(
                reminder(15.minutes),                                                    // once per occurrence
                EventAlarm(AlarmAction.Display, AlarmTrigger.Absolute(utc(day = 19, hour = 8))), // once for the series
            ),
        )
        // Lands on the very second the first event's reminders do, from an unrelated event.
        val colliding = eventAt(day = 11, alarms = listOf(reminder(24.hours + 15.minutes)))

        val alarms = listOf(
            crowded,
            series.occurrenceOn(day = 20),
            series.occurrenceOn(day = 21),
            series.occurrenceOn(day = 22),
            colliding,
        ).project()

        val ids = alarms.map(UpcomingAlarm::id)
        // 4 on the crowded event, 3 occurrences of the series reminder, 1 absolute firing, 1 colliding.
        assertEquals(9, alarms.size, "an alarm was silently dropped by the deduplication")
        assertEquals(9, ids.toSet().size, "two alarms would be scheduled under the same notification id")
    }

    private fun List<Event>.project(
        from: Instant = utc(day = 1, hour = 0),
        until: Instant = utc(day = 28, hour = 0),
        limit: Int = 50,
        actions: Set<AlarmAction> = setOf(AlarmAction.Display, AlarmAction.Audio),
    ) = upcomingAlarms(from = from, until = until, limit = limit, actions = actions, defaultZone = TimeZone.UTC)

    private fun reminder(before: Duration, uid: String? = null) =
        EventAlarm(AlarmAction.Display, AlarmTrigger.Relative(-before, TriggerRelation.Start), uid = uid?.let(AlarmId::Uid))

    /** An hour-long event on `2026-02-<day>` at 10:00 UTC. */
    private fun eventAt(day: Int, alarms: List<EventAlarm>): Event {
        val id = EventId("event://alarm-$day")
        return Event(
            masterEventId = id,
            occurrenceId = OccurrenceId.Master(id),
            calendarId = CalendarId("calendar://test"),
            accountId = AccountId(1L),
            title = "Test",
            timing = EventTiming(
                start = at(day, hour = 10),
                end = at(day, hour = 11),
                startTimeZone = TimeZone.UTC,
                endTimeZone = TimeZone.UTC,
                isAllDay = false,
            ),
            colors = EventColors.from(eventSourceColor = 0xFF2196F3.toInt(), calendarSourceColor = 0xFF2196F3.toInt()),
            canEdit = true,
            alarms = alarms,
        )
    }

    /** What the expansion hands us: the master re-timed onto one of its slots. */
    private fun Event.occurrenceOn(day: Int): Event = copy(
        occurrenceId = OccurrenceId.Recurrence(masterEventId, RecurrenceKey.Utc(utc(day, hour = 10))),
        timing = timing.copy(start = at(day, hour = 10), end = at(day, hour = 11)),
    )

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 2, day, hour, minute)

    private fun utc(day: Int, hour: Int, minute: Int = 0): Instant = at(day, hour, minute).toInstant(TimeZone.UTC)
}
