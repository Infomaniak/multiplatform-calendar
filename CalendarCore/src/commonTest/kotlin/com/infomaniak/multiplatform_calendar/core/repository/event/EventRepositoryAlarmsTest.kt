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
package com.infomaniak.multiplatform_calendar.core.repository.event

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal class EventRepositoryAlarmsTest : EventRepositoryTestBase() {

    /** Guards the widened read window [observeUpcomingAlarms] needs: a reminder can long precede its event. */
    @Test
    fun observeUpcomingAlarms_findsAReminderSetLongBeforeAnEventBeyondTheHorizon() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = dtStart,
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerRelative = (-20).days)),
        )

        val from = LocalDateTime(2026, 5, 20, 0, 0).toInstant(TimeZone.UTC)
        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = from,
            horizon = 10.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(1, alarms.size, "the event sits 26 days past the horizon, its reminder does not")
        assertEquals(LocalDateTime(2026, 5, 26, 10, 0).toInstant(TimeZone.UTC), alarms.single().firesAt)
    }

    /** A reminder further out than the fixed slack the read window used to be widened by. */
    @Test
    fun observeUpcomingAlarms_findsAReminderSetFurtherAheadThanAnyFixedMargin() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerRelative = (-40).days)),
        )

        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = LocalDateTime(2026, 5, 1, 0, 0).toInstant(TimeZone.UTC),
            horizon = 10.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            listOf(LocalDateTime(2026, 5, 6, 10, 0).toInstant(TimeZone.UTC)),
            alarms.map { it.firesAt },
            "how far an event is read past the horizon is its own reminder's offset, not a fixed margin",
        )
    }

    /** The wall-clock branches: a floating event has no absolute instant to shift the window against. */
    @Test
    fun observeUpcomingAlarms_findsAFarReminderOnAFloatingEvent() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerRelative = (-40).days)),
            zone = null,
        )

        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = LocalDateTime(2026, 5, 1, 0, 0).toInstant(TimeZone.UTC),
            horizon = 10.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            listOf(LocalDateTime(2026, 5, 6, 10, 0).toInstant(TimeZone.UTC)),
            alarms.map { it.firesAt },
            "a floating event is read on its wall-clock, shifted by its reminder all the same",
        )
    }

    /** All-day rows take the wall-clock branch too, being rendered as-is in the reader's zone. */
    @Test
    fun observeUpcomingAlarms_findsAFarReminderOnAnAllDayEvent() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 0, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerRelative = (-40).days)),
            zone = null,
            isAllDay = true,
        )

        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = LocalDateTime(2026, 5, 1, 0, 0).toInstant(TimeZone.UTC),
            horizon = 10.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            listOf(LocalDateTime(2026, 5, 6, 0, 0).toInstant(TimeZone.UTC)),
            alarms.map { it.firesAt },
        )
    }

    /** The floating recurrence branch, whose series bounds are wall-clock as well. */
    @Test
    fun observeUpcomingAlarms_findsAFarReminderOnAFloatingSeries() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerRelative = (-40).days)),
            id = EventId("event://floating-weekly"),
            rrule = RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 2),
            zone = null,
        )

        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = LocalDateTime(2026, 5, 1, 0, 0).toInstant(TimeZone.UTC),
            horizon = 10.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            listOf(LocalDateTime(2026, 5, 6, 10, 0).toInstant(TimeZone.UTC)),
            alarms.map { it.firesAt },
            "only the first of the two occurrences reminds within the window",
        )
    }

    /**
     * A dense series only reached by a fast-forward: widening the read window by more than the
     * reminder needs spends the expansion budget before the window itself, leaving nothing to fire.
     */
    @Test
    fun observeUpcomingAlarms_findsTheRemindersOfADenseSeriesStartingLongBeforeTheWindow() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 1, 1, 0, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerRelative = (-5).minutes)),
            id = EventId("event://secondly"),
            rrule = RecurrenceRule(freq = Frequency.Secondly),
        )

        val from = LocalDateTime(2026, 6, 1, 0, 0).toInstant(TimeZone.UTC)
        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = from,
            horizon = 30.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(10, alarms.size, "the series runs every second throughout the window")
        assertEquals(from, alarms.first().firesAt, "the occurrence five minutes into the window opens it")
    }

    /** An absolute trigger names an instant of its own: the event it hangs off may be nowhere near it. */
    @Test
    fun observeUpcomingAlarms_findsAnAbsoluteReminderFiringFarFromItsEvent() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        val firesAt = LocalDateTime(2026, 6, 3, 8, 0).toInstant(TimeZone.UTC)
        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 8, 15, 10, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerAbsolute = firesAt)),
        )

        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = LocalDateTime(2026, 6, 1, 0, 0).toInstant(TimeZone.UTC),
            horizon = 5.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(listOf(firesAt), alarms.map { it.firesAt }, "the event is read for its alarm, not for itself")
    }

    /** The same, on a series: the reminder answers to the master, no occurrence needs to stand in the window. */
    @Test
    fun observeUpcomingAlarms_findsAnAbsoluteReminderOnASeriesWithNoOccurrenceInTheWindow() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        val firesAt = LocalDateTime(2026, 6, 3, 8, 0).toInstant(TimeZone.UTC)
        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 8, 15, 10, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerAbsolute = firesAt)),
            rrule = RecurrenceRule(freq = Frequency.Daily),
        )

        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = LocalDateTime(2026, 6, 1, 0, 0).toInstant(TimeZone.UTC),
            horizon = 5.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(listOf(firesAt), alarms.map { it.firesAt }, "one firing for the whole series, not one per occurrence")
    }

    /** The read window is half-open where the expander's is, so its last instant must still be covered. */
    @Test
    fun observeUpcomingAlarms_findsAReminderFiringOnTheLastInstantOfTheHorizon() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 1, 10, 0),
            alarms = listOf(AlarmEntity(action = "DISPLAY", triggerRelative = (-15).minutes)),
            rrule = RecurrenceRule(freq = Frequency.Daily),
        )

        val from = LocalDateTime(2026, 6, 1, 0, 0).toInstant(TimeZone.UTC)
        val lastFiring = LocalDateTime(2026, 6, 3, 9, 45).toInstant(TimeZone.UTC)
        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = from,
            horizon = lastFiring - from,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(lastFiring, alarms.last().firesAt, "the horizon is the last instant an alarm can ring on")
    }

    /**
     * A floating event has no duration until a zone is picked, and a clock change makes the two disagree:
     * spanning the spring-forward, 01:30 to 03:30 in Paris lasts an hour, not the two its wall-clock reads.
     * A reminder counted from its end therefore rings an hour earlier than the stored bounds expect.
     */
    @Test
    fun observeUpcomingAlarms_findsAReminderCountedFromTheEndOfAFloatingEventSpanningAClockChange() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 3, 29, 1, 30),
            dtEnd = LocalDateTime(2026, 3, 29, 3, 30),
            alarms = listOf(
                AlarmEntity(action = "DISPLAY", triggerRelative = (-3).hours, triggerRelatedTo = TriggerRelation.End),
            ),
            zone = null,
        )

        val paris = TimeZone.of("Europe/Paris")
        val from = LocalDateTime(2026, 3, 28, 0, 0).toInstant(paris)
        val firesAt = LocalDateTime(2026, 3, 28, 23, 30).toInstant(paris)
        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = from,
            horizon = firesAt - from,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = paris,
        ).first()

        assertEquals(listOf(firesAt), alarms.map { it.firesAt }, "the hour the clock skips is an hour of the event")
    }

    @Test
    fun observeUpcomingAlarms_onlyReturnsTheRequestedActions() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://main")
        seedCalendar(account, calendarId)

        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        seedAlarmedEvent(
            calendarId = calendarId,
            dtStart = dtStart,
            alarms = listOf(
                AlarmEntity(action = "EMAIL", triggerRelative = (-30).minutes),
                AlarmEntity(action = "DISPLAY", triggerRelative = (-15).minutes),
            ),
        )

        val from = LocalDateTime(2026, 6, 1, 0, 0).toInstant(TimeZone.UTC)
        val alarms = repository.observeUpcomingAlarms(
            accountIds = setOf(account),
            from = from,
            horizon = 30.days,
            limit = 10,
            actions = setOf(AlarmAction.Display),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            listOf(LocalDateTime(2026, 6, 15, 9, 45).toInstant(TimeZone.UTC)),
            alarms.map { it.firesAt },
            "sending the mail is the server's job, not a device notification",
        )
    }

    private suspend fun seedAlarmedEvent(
        calendarId: CalendarId,
        dtStart: LocalDateTime,
        alarms: List<AlarmEntity>,
        id: EventId = EventId("event://alarmed"),
        rrule: RecurrenceRule? = null,
        zone: TimeZone? = TimeZone.UTC,
        isAllDay: Boolean = false,
        dtEnd: LocalDateTime = LocalDateTime(dtStart.date, LocalTime(dtStart.hour + 1, dtStart.minute)),
    ) {
        val timing = EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = dtEnd,
            startTimeZone = zone?.id,
            endTimeZone = zone?.id,
            // All-day rows are stored at UTC midnight even though they carry no zone, see EventTimingEntity.
            dtStartInstantMs = (zone ?: TimeZone.UTC.takeIf { isAllDay })
                ?.let { dtStart.toInstant(it).toEpochMilliseconds() },
            dtEndInstantMs = (zone ?: TimeZone.UTC.takeIf { isAllDay })
                ?.let { dtEnd.toInstant(it).toEpochMilliseconds() },
            isAllDay = isAllDay,
        )
        val event = EventEntity(
            id = id,
            calendarId = calendarId,
            content = EventContentEntity(
                summary = "Alarmed",
                timing = timing,
                alarms = alarms,
            ),
            rrule = rrule,
            hasRecurrence = rrule != null,
            recurrenceBounds = rrule?.let {
                toRecurrenceBoundsEntity(timing = timing, recurrenceRule = it, rDates = emptyList())
            },
            etag = "1",
        )
        eventDao().upsert(listOf(EventWithRawIcs(event, "")))
    }
}
