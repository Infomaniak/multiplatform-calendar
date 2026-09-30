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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionLimits
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import com.infomaniak.multiplatform_calendar.core.utils.ColorComputation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OccurrenceResolutionTest {

    @BeforeTest
    fun setUp() {
        ColorComputation.resetCache()
    }

    @Test
    fun resolveOccurrence_returnsGeneratedRuleOccurrence() = runTest {
        val master = dailyMaster("event://daily", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val requested = recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 2, 10, 0))

        val resolved = resolve(EventWithOverrides(master), requested)

        assertEquals(requested, resolved?.occurrenceId)
        assertEquals(LocalDateTime(2026, 1, 2, 10, 0), resolved?.timing?.start?.wallClock)
        assertEquals(master.title, resolved?.title)
        assertEquals(master.calendarId, resolved?.calendarId)
        assertEquals(master.colors, resolved?.colors)
        assertEquals(master.canEdit, resolved?.canEdit)
        assertEquals(master.timing.recurrenceRule, resolved?.timing?.recurrenceRule)
    }

    @Test
    fun resolveOccurrence_returnsNullWhenRuleDoesNotContainSlot() = runTest {
        val master = dailyMaster("event://daily", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val requested = recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 10, 10, 0))

        assertNull(resolve(EventWithOverrides(master), requested))
    }

    @Test
    fun resolveOccurrence_returnsLastOccurrenceWithinCount() = runTest {
        val master = dailyMaster("event://count", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))

        assertEquals(
            recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 3, 10, 0)),
            resolve(
                EventWithOverrides(master),
                recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 3, 10, 0)),
            )?.occurrenceId,
        )
    }

    @Test
    fun resolveOccurrence_returnsNullAfterCount() = runTest {
        val master = dailyMaster("event://count", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))

        assertNull(resolve(EventWithOverrides(master), recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 4, 10, 0))))
    }

    @Test
    fun resolveOccurrence_returnsOccurrenceWithinUntil() = runTest {
        val master = boundedMaster(untilDay = 3)

        assertEquals(
            recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 3, 10, 0)),
            resolve(
                EventWithOverrides(master),
                recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 3, 10, 0)),
            )?.occurrenceId,
        )
    }

    @Test
    fun resolveOccurrence_returnsNullAfterUntil() = runTest {
        val master = boundedMaster(untilDay = 3)

        assertNull(resolve(EventWithOverrides(master), recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 4, 10, 0))))
    }

    @Test
    fun resolveOccurrence_returnsNullForExDate() = runTest {
        val slot = LocalDateTime(2026, 1, 2, 10, 0)
        val base = dailyMaster("event://exdate", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val master =
            base.copy(timing = base.timing.copy(exDates = listOf(IcalDateValue.Zoned(slot.toInstant(TimeZone.UTC), "UTC"))))

        assertNull(resolve(EventWithOverrides(master), recurrenceId(master.masterEventId, slot)))
    }

    @Test
    fun resolveOccurrence_returnsRDateOccurrence() = runTest {
        val rDate = LocalDateTime(2026, 1, 10, 10, 0)
        val base = dailyMaster("event://rdate", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 1))
        val master =
            base.copy(timing = base.timing.copy(rDates = listOf(IcalDateValue.Zoned(rDate.toInstant(TimeZone.UTC), "UTC"))))
        val requested = recurrenceId(master.masterEventId, rDate)

        val resolved = resolve(EventWithOverrides(master), requested)

        assertEquals(requested, resolved?.occurrenceId)
        assertEquals(rDate, resolved?.timing?.start?.wallClock)
    }

    @Test
    fun resolveOccurrence_returnsMasterStartForRDateOnlySeries() = runTest {
        val base = dailyMaster("event://rdate-only", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 1))
        val master = base.copy(
            timing = base.timing.copy(
                recurrenceRule = null,
                rDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-10T10:00:00Z"), "UTC")),
            ),
        )

        assertEquals(
            recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 1, 10, 0)),
            resolve(
                EventWithOverrides(master),
                recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 1, 10, 0)),
            )?.occurrenceId,
        )
    }

    @Test
    fun resolveOccurrence_returnsRDateForRDateOnlySeries() = runTest {
        val base = dailyMaster("event://rdate-only", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 1))
        val master = base.copy(
            timing = base.timing.copy(
                recurrenceRule = null,
                rDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-10T10:00:00Z"), "UTC")),
            ),
        )

        assertEquals(
            recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 10, 10, 0)),
            resolve(
                EventWithOverrides(master),
                recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 10, 10, 0)),
            )?.occurrenceId,
        )
    }

    @Test
    fun resolveOccurrence_returnsNullForUnknownRDateOnlySlot() = runTest {
        val base = dailyMaster("event://rdate-only", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 1))
        val master = base.copy(
            timing = base.timing.copy(
                recurrenceRule = null,
                rDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-10T10:00:00Z"), "UTC")),
            ),
        )

        assertNull(resolve(EventWithOverrides(master), recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 6, 10, 0))))
    }

    @Test
    fun resolveOccurrence_returnsOverrideInsteadOfGeneratedOccurrence() = runTest {
        val master = dailyMaster("event://override", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val override = master.overrideAt(
            originalStart = LocalDateTime(2026, 1, 2, 10, 0),
            movedTo = LocalDateTime(2026, 1, 2, 15, 0),
        )
        val requested = OccurrenceId.Recurrence(master.masterEventId, override.first)

        val resolved = resolve(EventWithOverrides(master, mapOf(override)), requested)

        assertEquals(requested, resolved?.occurrenceId)
        assertEquals("Moved instance", resolved?.title)
        assertEquals(LocalDateTime(2026, 1, 2, 15, 0), resolved?.timing?.start?.wallClock)
    }

    @Test
    fun resolveOccurrence_matchesOverrideByTheoreticalSlot() = runTest {
        val master = dailyMaster("event://override-slot", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val override = master.overrideAt(
            originalStart = LocalDateTime(2026, 1, 2, 10, 0),
            movedTo = LocalDateTime(2026, 1, 3, 9, 0),
        )

        val moved =
            resolve(EventWithOverrides(master, mapOf(override)), OccurrenceId.Recurrence(master.masterEventId, override.first))
        val jan3 = resolve(
            EventWithOverrides(master, mapOf(override)),
            recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 3, 10, 0)),
        )

        assertEquals(LocalDateTime(2026, 1, 3, 9, 0), moved?.timing?.start?.wallClock)
        assertEquals(LocalDateTime(2026, 1, 3, 10, 0), jan3?.timing?.start?.wallClock)
    }

    @Test
    fun resolveOccurrence_returnsOverrideMovedFarFromOriginalSlot() = runTest {
        val master = dailyMaster("event://override-far", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val override = master.overrideAt(
            originalStart = LocalDateTime(2026, 1, 2, 10, 0),
            movedTo = LocalDateTime(2026, 3, 1, 10, 0),
        )

        assertEquals(
            OccurrenceId.Recurrence(master.masterEventId, override.first),
            resolve(
                EventWithOverrides(master, mapOf(override)),
                OccurrenceId.Recurrence(master.masterEventId, override.first),
            )?.occurrenceId,
        )
    }

    @Test
    fun resolveOccurrence_returnsNullForCancelledOverride() = runTest {
        val master = dailyMaster("event://cancelled", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val override = master.overrideAt(originalStart = LocalDateTime(2026, 1, 2, 10, 0), status = EventStatus.CANCELLED)

        assertNull(
            resolve(
                EventWithOverrides(master, mapOf(override)),
                OccurrenceId.Recurrence(master.masterEventId, override.first),
            ),
        )
    }

    @Test
    fun resolveOccurrence_dropsOverrideBeyondUntilAndReportsIt() = runTest {
        val master = boundedMaster(untilDay = 3)
        val orphan = master.overrideAt(originalStart = LocalDateTime(2026, 1, 10, 10, 0))
        val dropped = mutableListOf<Pair<EventId, RecurrenceKey>>()

        val resolved = resolve(
            series = EventWithOverrides(master, mapOf(orphan)),
            occurrenceId = OccurrenceId.Recurrence(master.masterEventId, orphan.first),
            onOrphanOverrideDropped = { masterId, slot -> dropped += masterId to slot },
        )

        assertNull(resolved)
        assertEquals(listOf(master.masterEventId to orphan.first), dropped)
    }

    @Test
    fun resolveOccurrence_keepsOverrideMovedPastUntilWhenSlotIsValid() = runTest {
        val master = boundedMaster(untilDay = 3)
        val override = master.overrideAt(
            originalStart = LocalDateTime(2026, 1, 2, 10, 0),
            movedTo = LocalDateTime(2026, 3, 1, 10, 0),
        )

        assertEquals(
            OccurrenceId.Recurrence(master.masterEventId, override.first),
            resolve(
                EventWithOverrides(master, mapOf(override)),
                OccurrenceId.Recurrence(master.masterEventId, override.first),
            )?.occurrenceId,
        )
    }

    @Test
    fun resolveOccurrence_keepsOverrideWhenRDateReAddsSlot() = runTest {
        val slot = LocalDateTime(2026, 1, 10, 10, 0)
        val master = boundedMaster(untilDay = 3, rDates = listOf(IcalDateValue.Zoned(slot.toInstant(TimeZone.UTC), "UTC")))
        val override = master.overrideAt(originalStart = slot)
        var orphanReported = false

        val resolved = resolve(
            series = EventWithOverrides(master, mapOf(override)),
            occurrenceId = OccurrenceId.Recurrence(master.masterEventId, override.first),
            onOrphanOverrideDropped = { _, _ -> orphanReported = true },
        )

        assertEquals(OccurrenceId.Recurrence(master.masterEventId, override.first), resolved?.occurrenceId)
        assertTrue(!orphanReported)
    }

    @Test
    fun resolveOccurrence_returnsOverrideOnExDateSlot() = runTest {
        val slot = LocalDateTime(2026, 1, 2, 10, 0)
        val base = dailyMaster("event://exdate-override", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val master =
            base.copy(timing = base.timing.copy(exDates = listOf(IcalDateValue.Zoned(slot.toInstant(TimeZone.UTC), "UTC"))))
        val override = master.overrideAt(originalStart = slot)

        assertEquals(
            OccurrenceId.Recurrence(master.masterEventId, override.first),
            resolve(
                EventWithOverrides(master, mapOf(override)),
                OccurrenceId.Recurrence(master.masterEventId, override.first),
            )?.occurrenceId,
        )
    }

    @Test
    fun resolveOccurrence_returnsNullWhenMasterIsNoLongerRecurring() = runTest {
        val recurring = dailyMaster("event://stale", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val nonRecurring = recurring.copy(timing = recurring.timing.copy(recurrenceRule = null, rDates = emptyList()))

        assertNull(
            resolve(
                EventWithOverrides(nonRecurring),
                recurrenceId(nonRecurring.masterEventId, LocalDateTime(2026, 1, 2, 10, 0)),
            ),
        )
    }

    @Test
    fun resolveOccurrence_returnsNullWhenOccurrenceBelongsToAnotherMaster() = runTest {
        val master = dailyMaster("event://a", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val requested = OccurrenceId.Recurrence(EventId("event://b"), RecurrenceKey.Utc(Instant.parse("2026-01-02T10:00:00Z")))

        val error = assertFailsWith<IllegalStateException> {
            resolve(EventWithOverrides(master), requested)
        }
        assertEquals(error.message?.contains("does not belong to master"), true)
    }

    @Test
    fun resolveOccurrence_supportsUtcRecurrence() = runTest {
        val master = dailyMaster("event://utc", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val requested = recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 2, 10, 0))

        val resolved = resolve(EventWithOverrides(master), requested)

        assertTrue(resolved != null)
        assertTrue((resolved.occurrenceId as OccurrenceId.Recurrence).recurrenceKey is RecurrenceKey.Utc)
        assertEquals(requested, resolved.occurrenceId)
    }

    @Test
    fun resolveOccurrence_supportsZonedRecurrence() = runTest {
        val zone = TimeZone.of("Europe/Zurich")
        val master = zonedMaster("event://zoned", zone, RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val requested =
            OccurrenceId.Recurrence(master.masterEventId, RecurrenceKey.Zoned(LocalDateTime(2026, 1, 2, 10, 0), zone.id))

        val resolved = resolve(EventWithOverrides(master), requested, timeZone = TimeZone.UTC)

        assertEquals(requested, resolved?.occurrenceId)
        assertEquals(LocalDateTime(2026, 1, 2, 10, 0), resolved?.timing?.start?.wallClock)
    }

    @Test
    fun resolveOccurrence_supportsFloatingRecurrence() = runTest {
        val master = floatingMaster("event://floating", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val requested = OccurrenceId.Recurrence(master.masterEventId, RecurrenceKey.Floating(LocalDateTime(2026, 1, 2, 10, 0)))

        val resolved = resolve(EventWithOverrides(master), requested, timeZone = TimeZone.of("Pacific/Honolulu"))

        assertEquals(requested, resolved?.occurrenceId)
        assertEquals(LocalDateTime(2026, 1, 2, 10, 0), resolved?.timing?.start?.wallClock)
    }

    @Test
    fun resolveOccurrence_supportsAllDayRecurrence() = runTest {
        val master = allDayMaster("event://all-day", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val requested = OccurrenceId.Recurrence(master.masterEventId, RecurrenceKey.AllDay(LocalDate(2026, 1, 2)))

        val resolved = resolve(EventWithOverrides(master), requested)

        assertEquals(requested, resolved?.occurrenceId)
        assertEquals(resolved?.timing?.isAllDay, true)
    }

    @Test
    fun resolveOccurrence_matchesExpansionAcrossSpringDstTransition() = runTest {
        val zone = TimeZone.of("Europe/Zurich")
        val master = zonedMaster(
            id = "event://dst-spring",
            zone = zone,
            rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
            start = LocalDateTime(2026, 3, 28, 10, 0),
        )

        val expanded = listOf(EventWithOverrides(master)).expandRecurrencesInWindow(
            rangeStart = utc(2026, 3, 27),
            rangeEnd = utc(2026, 4, 2),
            timeZone = TimeZone.UTC,
        )

        expanded.forEach { event ->
            val requested = event.occurrenceId as OccurrenceId.Recurrence
            val resolved = resolve(EventWithOverrides(master), requested, timeZone = TimeZone.UTC)
            assertEquals(event, resolved)
        }
    }

    @Test
    fun resolveOccurrence_matchesExpansionAcrossFallDstTransition() = runTest {
        val zone = TimeZone.of("Europe/Zurich")
        val master = zonedMaster(
            id = "event://dst-fall",
            zone = zone,
            rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
            start = LocalDateTime(2026, 10, 24, 10, 0),
        )

        val expanded = listOf(EventWithOverrides(master)).expandRecurrencesInWindow(
            rangeStart = utc(2026, 10, 23),
            rangeEnd = utc(2026, 10, 28),
            timeZone = TimeZone.UTC,
        )

        expanded.forEach { event ->
            val requested = event.occurrenceId as OccurrenceId.Recurrence
            val resolved = resolve(EventWithOverrides(master), requested, timeZone = TimeZone.UTC)
            assertEquals(event, resolved)
        }
    }

    @Test
    fun resolveOccurrence_matchesExpandedOccurrenceForEveryVisibleInstance() = runTest {
        val utcMaster = dailyMaster("event://utc", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val zoned = zonedMaster(
            id = "event://zoned",
            zone = TimeZone.of("Europe/Zurich"),
            rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val floating = floatingMaster("event://floating", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val allDay = allDayMaster("event://all-day", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val exDateBase = dailyMaster("event://exdate", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val withExDate = exDateBase.copy(
            timing = exDateBase.timing.copy(exDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-02T10:00:00Z"), "UTC"))),
        )
        val overrideBase = dailyMaster("event://override", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val movedOverride = overrideBase.overrideAt(
            originalStart = LocalDateTime(2026, 1, 2, 10, 0),
            movedTo = LocalDateTime(2026, 1, 2, 15, 0),
        )

        val series = listOf(
            EventWithOverrides(utcMaster),
            EventWithOverrides(zoned),
            EventWithOverrides(floating),
            EventWithOverrides(allDay),
            EventWithOverrides(withExDate),
            EventWithOverrides(overrideBase, mapOf(movedOverride)),
        )
        val expanded = series.expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 20),
            timeZone = TimeZone.UTC,
        )
        val byMaster = series.associateBy { it.master.masterEventId }

        expanded.forEach { occurrence ->
            val occurrenceId = occurrence.occurrenceId as? OccurrenceId.Recurrence ?: return@forEach
            val resolved = resolve(byMaster.getValue(occurrence.masterEventId), occurrenceId)
            assertEquals(occurrence, resolved)
        }
    }

    @Test
    fun resolveOccurrence_reportsScannedInstanceCap() = runTest {
        val master = dailyMaster(
            "event://cap",
            RecurrenceRule(freq = Frequency.Secondly, occurrenceCount = 1_000_000),
        )
        val requested = recurrenceId(master.masterEventId, LocalDateTime(2030, 1, 1, 10, 0))
        val truncations = mutableListOf<Pair<EventId, ExpansionOutcome>>()

        val resolved = resolve(
            series = EventWithOverrides(master),
            occurrenceId = requested,
            limits = ExpansionLimits(maxScannedInstances = 100),
            onExpansionTruncated = { masterId, outcome -> truncations += masterId to outcome },
        )

        assertNull(resolved)
        assertEquals(listOf(master.masterEventId to ExpansionOutcome.StoppedByScannedInstanceCap), truncations)
    }

    @Test
    fun resolveOccurrence_neverReturnsWrongOccurrenceIdOnInvalidCases() = runTest {
        val base = dailyMaster("event://invariant", RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        val master = base.copy(
            timing = base.timing.copy(exDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-02T10:00:00Z"), "UTC"))),
        )
        val cancelledOverride =
            master.overrideAt(originalStart = LocalDateTime(2026, 1, 3, 10, 0), status = EventStatus.CANCELLED)
        val nonRecurring = base.copy(timing = base.timing.copy(recurrenceRule = null, rDates = emptyList()))

        val nullCases = listOf(
            EventWithOverrides(master) to recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 10, 10, 0)), // after count
            EventWithOverrides(master) to recurrenceId(master.masterEventId, LocalDateTime(2026, 1, 2, 10, 0)),  // EXDATE
            EventWithOverrides(master, mapOf(cancelledOverride)) to OccurrenceId.Recurrence(
                master.masterEventId,
                cancelledOverride.first,
            ),
            EventWithOverrides(nonRecurring) to recurrenceId(nonRecurring.masterEventId, LocalDateTime(2026, 1, 2, 10, 0)),
        )

        nullCases.forEach { (series, request) ->
            val resolved = resolve(series, request)
            if (resolved != null) assertEquals(request, resolved.occurrenceId)
        }

        assertFailsWith<IllegalStateException> {
            val occurrenceId = OccurrenceId.Recurrence(
                masterId = EventId("event://other"),
                recurrenceKey = RecurrenceKey.Utc(Instant.parse("2026-01-02T10:00:00Z")),
            )
            resolve(
                series = EventWithOverrides(master),
                occurrenceId = occurrenceId,
            )
        }
    }

    private suspend fun resolve(
        series: EventWithOverrides,
        occurrenceId: OccurrenceId.Recurrence,
        timeZone: TimeZone = TimeZone.UTC,
        limits: ExpansionLimits = ExpansionLimits(),
        onExpansionTruncated: (masterId: EventId, outcome: ExpansionOutcome) -> Unit = { _, _ -> },
        onOrphanOverrideDropped: (masterId: EventId, slot: RecurrenceKey) -> Unit = { _, _ -> },
    ): Event? {
        return series.resolveOccurrence(
            occurrenceId = occurrenceId,
            timeZone = timeZone,
            limits = limits,
            onExpansionTruncated = onExpansionTruncated,
            onOrphanOverrideDropped = onOrphanOverrideDropped,
        )
    }

    private fun recurrenceId(masterId: EventId, start: LocalDateTime): OccurrenceId.Recurrence {
        return OccurrenceId.Recurrence(masterId = masterId, recurrenceKey = RecurrenceKey.Utc(start.toInstant(TimeZone.UTC)))
    }

    private fun dailyMaster(id: String, rule: RecurrenceRule): Event = Event(
        masterEventId = EventId(id),
        occurrenceId = OccurrenceId.Master(EventId(id)),
        calendarId = CalendarId("calendar://test"),
        accountId = AccountId(1L),
        title = "Test",
        timing = EventTiming(
            start = EventDateTime.of(LocalDateTime(2026, 1, 1, 10, 0), TimeZone.UTC),
            end = EventDateTime.of(LocalDateTime(2026, 1, 1, 11, 0), TimeZone.UTC),
            isAllDay = false,
            recurrenceRule = rule,
        ),
        colors = EventColors.from(eventSourceColor = 0xFF2196F3.toInt(), calendarSourceColor = 0xFF2196F3.toInt()),
        canEdit = true,
    )

    private fun zonedMaster(
        id: String,
        zone: TimeZone,
        rule: RecurrenceRule,
        start: LocalDateTime = LocalDateTime(2026, 1, 1, 10, 0),
    ): Event = Event(
        masterEventId = EventId(id),
        occurrenceId = OccurrenceId.Master(EventId(id)),
        calendarId = CalendarId("calendar://test"),
        accountId = AccountId(1L),
        title = "Test",
        timing = EventTiming(
            start = EventDateTime.of(start, zone),
            end = EventDateTime.of(LocalDateTime(start.date, LocalTime(start.hour + 1, start.minute)), zone),
            isAllDay = false,
            recurrenceRule = rule,
        ),
        colors = EventColors.from(eventSourceColor = 0xFF2196F3.toInt(), calendarSourceColor = 0xFF2196F3.toInt()),
        canEdit = true,
    )

    private fun floatingMaster(id: String, rule: RecurrenceRule): Event = Event(
        masterEventId = EventId(id),
        occurrenceId = OccurrenceId.Master(EventId(id)),
        calendarId = CalendarId("calendar://test"),
        accountId = AccountId(1L),
        title = "Test",
        timing = EventTiming(
            start = EventDateTime.of(LocalDateTime(2026, 1, 1, 10, 0), null),
            end = EventDateTime.of(LocalDateTime(2026, 1, 1, 11, 0), null),
            isAllDay = false,
            recurrenceRule = rule,
        ),
        colors = EventColors.from(eventSourceColor = 0xFF2196F3.toInt(), calendarSourceColor = 0xFF2196F3.toInt()),
        canEdit = true,
    )

    private fun allDayMaster(id: String, rule: RecurrenceRule): Event = Event(
        masterEventId = EventId(id),
        occurrenceId = OccurrenceId.Master(EventId(id)),
        calendarId = CalendarId("calendar://test"),
        accountId = AccountId(1L),
        title = "Test",
        timing = EventTiming(
            start = EventDateTime.of(LocalDateTime(2026, 1, 1, 0, 0), null),
            end = EventDateTime.of(LocalDateTime(2026, 1, 2, 0, 0), null),
            isAllDay = true,
            recurrenceRule = rule,
        ),
        colors = EventColors.from(eventSourceColor = 0xFF2196F3.toInt(), calendarSourceColor = 0xFF2196F3.toInt()),
        canEdit = true,
    )

    private fun boundedMaster(untilDay: Int, rDates: List<IcalDateValue> = emptyList()): Event {
        val base = dailyMaster(
            id = "event://bounded",
            rule = RecurrenceRule(
                freq = Frequency.Daily,
                until = RecurrenceUntil.DateTimeUtc(LocalDateTime(2026, 1, untilDay, 23, 59, 59).toInstant(TimeZone.UTC)),
            ),
        )
        return base.copy(timing = base.timing.copy(rDates = rDates))
    }

    private fun Event.overrideAt(
        originalStart: LocalDateTime,
        movedTo: LocalDateTime = originalStart,
        status: EventStatus? = null,
    ): Pair<RecurrenceKey, Event> {
        val key = RecurrenceKey.Utc(originalStart.toInstant(TimeZone.UTC))
        val override = copy(
            occurrenceId = OccurrenceId.Recurrence(masterEventId, key),
            title = "Moved instance",
            status = status,
            timing = timing.copy(
                start = EventDateTime.of(movedTo, timing.startTimeZone),
                end = EventDateTime.of(LocalDateTime(movedTo.date, LocalTime(movedTo.hour + 1, movedTo.minute)), timing.endTimeZone),
                recurrenceRule = null,
            ),
        )
        return key to override
    }

    private fun utc(year: Int, month: Int, day: Int): Instant {
        return LocalDateTime(year, month, day, 0, 0).toInstant(TimeZone.UTC)
    }
}



