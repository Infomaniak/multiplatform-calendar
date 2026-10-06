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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.mapper.toEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceTarget
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Organizer
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAttendeesChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDateListChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDateListLine
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOrganizerChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteVeventSeed
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

internal class EventRepositoryUpdateFollowingTest : EventRepositoryTestBase() {

    @Test
    fun updateEvent_thisAndFollowing_editsTheTailAttendeesFromItsSeed() = runTest {
        seedCalendar()
        val base = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        val master = base.copy(content = base.content.copy(attendees = listOf(ALICE), organizer = OWNER))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(
                occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)),
                RecurrenceScope.ThisAndFollowing,
            ),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
                attendees = listOf(ALICE.toEdit()),
                organizer = Organizer(OWNER.email, OWNER.displayName),
            ),
        )

        // The tail is cloned from the master, which already carries these participants.
        val built = fakeCaldav.builds.single()
        assertEquals(RemoteAttendeesChange.Unchanged, built.attendeesChange)
        assertEquals(RemoteOrganizerChange.Unchanged, built.organizerChange)
    }

    @Test
    fun updateEvent_thisAndFollowing_countsTheInstancesOnEachSideOfThePivot() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
                start = LocalDateTime(2026, 6, 17, 14, 0),
                end = LocalDateTime(2026, 6, 17, 15, 0),
            ),
        )

        // The 15th and the 16th stay behind, the 17th to the 19th leave with the edit.
        assertEquals("FREQ=DAILY;COUNT=2", rruleOf(fakeCaldav.patches.single()))
        val built = fakeCaldav.builds.single()
        assertEquals("FREQ=DAILY;COUNT=3", rruleOf(built))
        assertEquals("20260617T140000Z", built.dtStart)
        assertEquals("Tail", built.summary)
        assertEquals(1, fakeCaldav.creates.size, "the tail is a resource of its own")
    }

    @Test
    fun updateEvent_thisAndFollowing_carriesAnEndDateOverToTheTail() = runTest {
        seedCalendar()
        val until = RecurrenceUntil.DateTimeUtc(LocalDateTime(2026, 6, 19, 10, 0).toInstant(TimeZone.UTC))
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, until = until),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, until = until),
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ),
        )

        // An end date is a date, not a count: the tail keeps it as it stands and the head ends earlier.
        assertEquals("FREQ=DAILY;UNTIL=20260619T100000Z", rruleOf(fakeCaldav.builds.single()))
        assertEquals("FREQ=DAILY;UNTIL=20260616T100000Z", rruleOf(fakeCaldav.patches.single()))
    }

    /**
     * A tail is built from scratch, so its alarms are restated through the domain model. A `VALARM` whose
     * `TRIGGER` cannot be read has no domain form to be restated in, and is left behind — the head keeps
     * it, the tail is born without it. Pinned here because it is a deliberate loss, not an oversight.
     */
    @Test
    fun updateEvent_thisAndFollowing_leavesAnUnreadableAlarmBehindOnTheHead() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        ).let {
            it.copy(
                content = it.content.copy(
                    alarms = listOf(
                        AlarmEntity(action = "DISPLAY", triggerRelative = -(15.minutes)),
                        AlarmEntity(action = "DISPLAY"), // No trigger the domain can read.
                    ),
                ),
            )
        }
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ).copy(alarms = AlarmListEdit.Preserve),
        )

        val tailAlarms = assertNotNull(fakeCaldav.builds.single().alarms, "the tail states the alarms it carries")
        assertEquals(1, tailAlarms.size, "only the alarm the domain can read makes it onto the tail")
        assertEquals("-PT15M", tailAlarms.single().triggerDuration)
        assertNull(fakeCaldav.patches.single().alarms, "the head is truncated, its own alarms untouched")
    }

    @Test
    fun updateEvent_thisAndFollowing_fromTheFirstOccurrence_movesTheWholeSeries() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 15, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Renamed",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            ),
        )

        // Nothing precedes the first instance, so there is no series to leave behind and none to create.
        assertEquals(listOf("patch"), fakeCaldav.calls)
        assertEquals("Renamed", fakeCaldav.patches.single().summary)
    }

    @Test
    fun updateEvent_thisAndFollowing_writesTheTailBeforeEndingTheMaster() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 18, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            ),
        )

        // Should the patch fail, the tail shows twice — which beats losing it outright.
        assertEquals(listOf("build", "override", "create", "patch"), fakeCaldav.calls)
    }

    @Test
    fun updateEvent_thisAndFollowing_movesTheTailsDateListsAlongWithIt() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            rDates = listOf(icalUtc(2026, 6, 20)),
        ).copy(exDates = listOf(icalUtc(2026, 6, 16), icalUtc(2026, 6, 18)))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
                start = LocalDateTime(2026, 6, 17, 11, 0),
                end = LocalDateTime(2026, 6, 17, 12, 0),
            ),
        )

        val built = fakeCaldav.builds.single()
        // The 16th stays with the head; the 18th and the 20th follow the tail, moved by the same hour it was.
        assertEquals(listOf("20260618T110000Z"), dateListOf(built.exDateChange))
        assertEquals(listOf("20260620T110000Z"), dateListOf(built.rDateChange))
        assertEquals(listOf("20260616T100000Z"), dateListOf(fakeCaldav.patches.single().exDateChange))
    }

    @Test
    fun updateEvent_thisAndFollowing_takesTheTailsOverridesAlongWithIt() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        val kept = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 16, 10, 0))
        val moved = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 18, 10, 0),
            movedTo = LocalDateTime(2026, 6, 18, 15, 0),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(kept, moved))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
                start = LocalDateTime(2026, 6, 17, 11, 0),
                end = LocalDateTime(2026, 6, 17, 12, 0),
            ),
        )

        val (recurrenceId, edit) = fakeCaldav.overrideUpserts.single()
        // Only the 18th leaves, and both the slot it stands for and what it shows move by that hour.
        assertEquals("20260618T110000Z", recurrenceId.value)
        assertEquals("20260618T160000Z", edit.dtStart)
        assertEquals("Moved instance", edit.summary)
    }

    @Test
    fun updateEvent_thisAndFollowing_fromAnOverriddenPivot_doesNotCarryThePivotOverride() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        val pivot = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 17, 10, 0),
            movedTo = LocalDateTime(2026, 6, 17, 15, 0),
        )
        val after = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 18, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(pivot, after))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                start = LocalDateTime(2026, 6, 17, 11, 0),
                end = LocalDateTime(2026, 6, 17, 12, 0),
            ),
        )

        // The tail already stands for the pivot, so carrying its override would displace it twice.
        val (recurrenceId, _) = fakeCaldav.overrideUpserts.single()
        assertEquals("20260618T110000Z", recurrenceId.value)
        // And the tail is built from the pivot's own VEVENT, which is what holds its content.
        assertEquals("20260617T100000Z", fakeCaldav.buildSeeds.single()?.recurrenceId?.value)
    }

    @Test
    fun updateEvent_thisAndFollowing_reencodesTheDateListsWhenTheTailTurnsFloating() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            rDates = listOf(icalUtc(2026, 6, 20)),
        ).copy(exDates = listOf(icalUtc(2026, 6, 18)))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
                start = LocalDateTime(2026, 6, 17, 11, 0),
                end = LocalDateTime(2026, 6, 17, 12, 0),
                timeZone = null,
            ),
        )

        val built = fakeCaldav.builds.single()
        // A UTC value would designate nothing on a floating series: both take its DTSTART form.
        assertEquals(listOf("20260618T110000"), dateListOf(built.exDateChange))
        assertEquals(listOf("20260620T110000"), dateListOf(built.rDateChange))
    }

    @Test
    fun updateEvent_thisAndFollowing_reencodesTheDateListsWhenTheTailTurnsAllDay() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            rDates = listOf(icalUtc(2026, 6, 20)),
        ).copy(exDates = listOf(icalUtc(2026, 6, 18)))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
                start = LocalDateTime(2026, 6, 17, 0, 0),
                end = LocalDateTime(2026, 6, 18, 0, 0),
                timeZone = null,
                isAllDay = true,
            ),
        )

        val built = fakeCaldav.builds.single()
        // An all-day series reads DATE values only, so the excluded and added days keep their meaning.
        assertEquals(listOf("20260618"), dateListOf(built.exDateChange))
        assertEquals(listOf("20260620"), dateListOf(built.rDateChange))
    }

    @Test
    fun updateEvent_thisAndFollowing_reencodesACarriedOverrideWhenTheTailTurnsFloating() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 18, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                start = LocalDateTime(2026, 6, 17, 11, 0),
                end = LocalDateTime(2026, 6, 17, 12, 0),
                timeZone = null,
            ),
        )

        val (recurrenceId, _) = fakeCaldav.overrideUpserts.single()
        // Its UTC RECURRENCE-ID would pair with no instance of a floating tail, so it takes that form.
        assertEquals("20260618T110000", recurrenceId.value)
        assertNull(recurrenceId.tzid)
    }

    @Test
    fun updateEvent_thisAndFollowing_keepsTheLastOccurrenceWhenTheMovedTailRunsPastItsEndDate() = runTest {
        seedCalendar()
        val until = RecurrenceUntil.DateTimeUtc(LocalDateTime(2026, 6, 19, 10, 0).toInstant(TimeZone.UTC))
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, until = until),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, until = until),
                start = LocalDateTime(2026, 6, 17, 14, 0),
                end = LocalDateTime(2026, 6, 17, 15, 0),
            ),
        )

        // The end date moves with the series: left at 10:00 it would cut the 19th, four hours short.
        assertEquals("FREQ=DAILY;UNTIL=20260619T140000Z", rruleOf(fakeCaldav.builds.single()))
    }

    @Test
    fun updateEvent_thisAndFollowing_carriesAnOverrideTheNewRuleNoLongerGenerates() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 18, 10, 0))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 3),
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ),
        )

        // A weekly tail no longer generates the 18th, but RFC 5545 lets the override stand on its own:
        // dropping what the user wrote into it would lose more than it would tidy up.
        val (recurrenceId, _) = fakeCaldav.overrideUpserts.single()
        assertEquals("20260618T100000Z", recurrenceId.value)
    }

    @Test
    fun updateEvent_thisAndFollowing_onAnExcludedOccurrence_doesNothing() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        ).copy(exDates = listOf(icalUtc(2026, 6, 17)))
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                start = LocalDateTime(2026, 6, 17, 11, 0),
                end = LocalDateTime(2026, 6, 17, 12, 0),
            ),
        )

        // Only a target left over from before the exclusion reaches here: splitting would resurrect it.
        assertEquals(emptyList(), fakeCaldav.calls)
        assertEquals("Daily recurring", eventDao().getEvent(master.id)?.content?.summary)
    }

    @Test
    fun updateEvent_thisAndFollowing_seedsTheTailAndItsOverridesFromTheSourceIcs() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        val override = overrideEntity(master.id, originalStart = LocalDateTime(2026, 6, 18, 10, 0))
        val sourceIcs = "BEGIN:VCALENDAR\nORGANIZER:mailto:a@b.c\nEND:VCALENDAR"
        eventDao().upsert(listOf(EventWithRawIcs(master, sourceIcs, listOf(override))))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ),
        )

        // What the bridge clones is what survives the split: organizer, attendees, STATUS, custom
        // properties. This asserts the source reaches it — the cloning itself belongs to the Rust side.
        assertEquals(RemoteVeventSeed(sourceIcs, recurrenceId = null), fakeCaldav.buildSeeds.single())
        val overrideSeed = fakeCaldav.overrideSeeds.single()
        assertEquals(sourceIcs, overrideSeed?.icsData)
        assertEquals("20260618T100000Z", overrideSeed?.recurrenceId?.value)
    }

    @Test
    fun updateEvent_thisAndFollowing_givesTheTailTheRuleTheEditAsksFor() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        repository.updateEvent(
            credentials = CREDENTIALS,
            target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 17, 10, 0)), RecurrenceScope.ThisAndFollowing),
            // The user turned the rest of the series weekly: that is the rule the tail must carry.
            data = editData(
                title = "Tail",
                recurrence = RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 4),
                start = LocalDateTime(2026, 6, 17, 10, 0),
                end = LocalDateTime(2026, 6, 17, 11, 0),
            ),
        )

        assertEquals("FREQ=WEEKLY;COUNT=4", rruleOf(fakeCaldav.builds.single()))
        // The head is bounded on what the series used to be, the edit being about the tail only.
        assertEquals("FREQ=DAILY;COUNT=2", rruleOf(fakeCaldav.patches.single()))
    }

    @Test
    fun updateEvent_thisAndFollowing_atAnOccurrenceTheRuleDoesNotGenerate_isRejected() = runTest {
        seedCalendar()
        // The 16th at 15:00 is an RDATE: the rule only ever produces 10:00 instances.
        val master = recurringColorMaster(
            eventId = EventId("https://cal/main/series.ics"),
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            rDates = listOf(IcalDateValue.Zoned(LocalDateTime(2026, 6, 16, 15, 0).toInstant(TimeZone.UTC), TimeZone.UTC.id)),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "BEGIN:VEVENT", emptyList())))

        // Re-anchoring the rule on 15:00 would push every later instance five hours away from where it stands.
        assertFailsWith<IllegalArgumentException> {
            repository.updateEvent(
                credentials = CREDENTIALS,
                target = OccurrenceTarget.Recurring(occurrenceOf(master.id, LocalDateTime(2026, 6, 16, 15, 0)), RecurrenceScope.ThisAndFollowing),
                data = editData(
                    title = "Tail",
                    recurrence = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
                    start = LocalDateTime(2026, 6, 16, 15, 0),
                    end = LocalDateTime(2026, 6, 16, 16, 0),
                ),
            )
        }
        assertEquals(emptyList(), fakeCaldav.calls, "nothing may be written when the split is refused")
    }

    private fun rruleOf(edit: RemoteEventEdit) = assertIs<RemoteRecurrenceChange.Set>(edit.recurrenceChange).value

    private fun dateListOf(change: RemoteDateListChange) =
        assertIs<RemoteDateListChange.Set>(change).lines.flatMap(RemoteDateListLine::values)
}
