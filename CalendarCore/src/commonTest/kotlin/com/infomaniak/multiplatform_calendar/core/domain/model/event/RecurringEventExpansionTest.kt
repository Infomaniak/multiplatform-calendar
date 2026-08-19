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
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionLimits
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurringEventExpansionTest {

    @Test
    fun reportsTruncationWithMasterIdAndOutcome() = runTest {
        val master = dailyMaster(id = "event://daily", rule = RecurrenceRule(freq = Frequency.Daily))
        val truncations = mutableListOf<Pair<EventId, ExpansionOutcome>>()

        listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
            limits = ExpansionLimits(maxGeneratedOccurrences = 3),
            onExpansionTruncated = { masterId, outcome -> truncations += masterId to outcome },
        )

        assertEquals(EventId("event://daily") to ExpansionOutcome.TruncatedByOccurrenceCap, truncations.single())
    }

    @Test
    fun doesNotReportWhenSeriesCompletes() = runTest {
        val master = dailyMaster(id = "event://daily", rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3))
        var reported = false

        listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
            onExpansionTruncated = { _, _ -> reported = true },
        )

        assertFalse(reported, "a series that terminates naturally must not be reported as truncated")
    }

    @Test
    fun reportsTruncationForScannedInstanceCapOutcome() = runTest {
        val master = dailyMaster(
            id = "event://secondly",
            rule = RecurrenceRule(freq = Frequency.Secondly, occurrenceCount = 1_000_000),
        )
        val truncations = mutableListOf<Pair<EventId, ExpansionOutcome>>()

        listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2030, 1, 1),
            rangeEnd = utc(2100, 1, 1),
            timeZone = TimeZone.UTC,
            limits = ExpansionLimits(maxScannedInstances = 100),
            onExpansionTruncated = { masterId, outcome -> truncations += masterId to outcome },
        )

        assertEquals(EventId("event://secondly") to ExpansionOutcome.StoppedByScannedInstanceCap, truncations.single())
    }

    @Test
    fun reportsTruncationForConsecutiveEmptyPeriodsOutcome() = runTest {
        // FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=31 never resolves (Feb 31): only the forced DTSTART is emitted,
        // then the empty-period streak trips the cap — which must still be reported as a truncation.
        val master = dailyMaster(
            id = "event://feb31",
            rule = RecurrenceRule(freq = Frequency.Yearly, byMonth = listOf(2), byMonthDay = listOf(31)),
        )
        val truncations = mutableListOf<Pair<EventId, ExpansionOutcome>>()

        listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2999, 1, 1),
            timeZone = TimeZone.UTC,
            limits = ExpansionLimits(maxScannedPeriods = 50),
            onExpansionTruncated = { masterId, outcome -> truncations += masterId to outcome },
        )

        assertEquals(EventId("event://feb31") to ExpansionOutcome.StoppedByConsecutiveEmptyPeriods, truncations.single())
    }

    @Test
    fun occurrenceIdIsStableRegardlessOfObservationWindow() = runTest {
        // A given occurrence must keep the same synthetic `masterId#key` id whatever window it is seen
        // through, so UI diffing stays stable — i.e. the key is anchored to the occurrence, not its
        // index within the returned list.
        val master = dailyMaster(id = "event://daily", rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5))

        suspend fun idsByStart(rangeStart: Instant): Map<LocalDateTime, String> = listOf(master)
            .expandRecurrencesInWindow(rangeStart = rangeStart, rangeEnd = utc(2026, 1, 11), timeZone = TimeZone.UTC)
            .associate { it.timing.start to it.occurrenceId.value }

        val wide = idsByStart(utc(2026, 1, 1))   // Jan 1..5
        val narrow = idsByStart(utc(2026, 1, 3)) // Jan 3..5 — same occurrences, later window start

        assertTrue(narrow.isNotEmpty(), "the later window must still contain occurrences")
        narrow.forEach { (start, id) -> assertEquals(wide[start], id, "occurrence $start changed id across windows") }
    }

    @Test
    fun nonRecurringEventsPassThroughUnchanged() = runTest {
        val plain = dailyMaster(id = "event://plain", rule = RecurrenceRule(freq = Frequency.Daily))
            .let { it.copy(timing = it.timing.copy(recurrenceRule = null)) }
        val recurring = dailyMaster(id = "event://daily", rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 2))

        val result = listOf(plain, recurring).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        // The plain event passes through as-is (same instance, no synthetic key) while the master is replaced by
        // its 2 occurrences → 3 events total.
        assertEquals(3, result.size)
        assertTrue(result.contains(plain), "a non-recurring event must pass through untouched")
        assertTrue(
            result.none { it.masterEventId == recurring.masterEventId && it.occurrenceId == recurring.occurrenceId },
            "the recurring master itself must not survive expansion",
        )
        assertEquals(2, result.count { it.occurrenceId.value.startsWith("event://daily#") })
    }

    @Test
    fun emptyInputYieldsEmptyResult() = runTest {
        val result = emptyList<Event>().expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        assertTrue(result.isEmpty(), "an empty input must not crash and must yield an empty result")
    }

    @Test
    fun occurrencePreservesMasterFieldsIncludingRecurrenceRule() = runTest {
        val rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 2)
        val master = dailyMaster(id = "event://daily", rule = rule)

        val occurrences = listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        assertEquals(2, occurrences.size)
        occurrences.forEach { occurrence ->
            assertTrue(occurrence.occurrenceId.value.startsWith("event://daily#"), "each occurrence gets its synthetic id")
            assertEquals(master.title, occurrence.title, "title must be copied from the master")
            assertEquals(master.colors, occurrence.colors, "colors must be copied from the master")
            assertEquals(master.canEdit, occurrence.canEdit, "editability must be copied from the master")
            assertEquals(
                rule,
                occurrence.timing.recurrenceRule,
                "the RRULE stays on the occurrence so consumers can still tell it belongs to a series",
            )
        }
    }

    @Test
    fun recurrenceExpansion_includesRDateOutsideRrulePattern() = runTest {
        val master = dailyMaster(id = "event://rdate", rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 2)).let {
            it.copy(
                timing = it.timing.copy(
                    rDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-10T10:00:00Z"), TimeZone.UTC.id)),
                ),
            )
        }

        val result = listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        assertTrue(result.any { it.timing.start == LocalDateTime(2026, 1, 10, 10, 0) })
    }

    @Test
    fun recurrenceExpansion_excludesExDateFromGeneratedSet() = runTest {
        val master = dailyMaster(id = "event://exdate", rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3)).let {
            it.copy(
                timing = it.timing.copy(
                    exDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-02T10:00:00Z"), TimeZone.UTC.id)),
                ),
            )
        }

        val result = listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        assertFalse(result.any { it.timing.start == LocalDateTime(2026, 1, 2, 10, 0) })
        assertEquals(2, result.size)
    }

    @Test
    fun recurrenceExpansion_exdateWithDifferentTzidStillExcludesZonedMasterOccurrence() = runTest {
        val paris = TimeZone.of("Europe/Paris")
        val master = dailyMaster(
            id = "event://exdate-zoned",
            rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        ).copy(
            timing = EventTiming(
                start = LocalDateTime(2026, 1, 1, 10, 0),
                end = LocalDateTime(2026, 1, 1, 11, 0),
                startTimeZone = paris,
                endTimeZone = paris,
                isAllDay = false,
                recurrenceRule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                exDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-02T09:00:00Z"), "America/New_York")),
            ),
        )

        val result = listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        assertFalse(result.any { it.timing.start == LocalDateTime(2026, 1, 2, 10, 0) })
        assertEquals(2, result.size)
    }

    @Test
    fun recurrenceExpansion_exdateUtcExcludesUtcMasterOccurrence() = runTest {
        val base = dailyMaster(
            id = "event://exdate-utc",
            rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val master = base.copy(
            timing = base.timing.copy(
                recurrenceRule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                exDates = listOf(IcalDateValue.Zoned(Instant.parse("2026-01-02T10:00:00Z"), "Europe/Zurich")),
            ),
        )

        val result = listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        assertFalse(result.any { it.timing.start == LocalDateTime(2026, 1, 2, 10, 0) })
        assertEquals(2, result.size)
    }

    @Test
    fun recurrenceExpansion_exdateFloatingExcludesFloatingMasterOccurrence() = runTest {
        val master = dailyMaster(
            id = "event://exdate-floating",
            rule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        ).copy(
            timing = EventTiming(
                start = LocalDateTime(2026, 1, 1, 10, 0),
                end = LocalDateTime(2026, 1, 1, 11, 0),
                startTimeZone = null,
                endTimeZone = null,
                isAllDay = false,
                recurrenceRule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
                exDates = listOf(IcalDateValue.Floating(LocalDateTime(2026, 1, 2, 10, 0))),
            ),
        )

        val result = listOf(master).expandRecurrencesInWindow(
            rangeStart = utc(2026, 1, 1),
            rangeEnd = utc(2026, 1, 11),
            timeZone = TimeZone.UTC,
        )

        assertFalse(result.any { it.timing.start == LocalDateTime(2026, 1, 2, 10, 0) })
        assertEquals(2, result.size)
    }

    private fun dailyMaster(id: String, rule: RecurrenceRule): Event = Event(
        masterEventId = EventId(id),
        occurrenceId = OccurrenceId(id),
        calendarId = CalendarId("calendar://test"),
        accountId = AccountId(1L),
        title = "Test",
        timing = EventTiming(
            start = LocalDateTime(2026, 1, 1, 10, 0),
            end = LocalDateTime(2026, 1, 1, 11, 0),
            startTimeZone = TimeZone.UTC,
            endTimeZone = TimeZone.UTC,
            isAllDay = false,
            recurrenceRule = rule,
        ),
        colors = EventColors.from(CalendarColors.from(0xFF2196F3.toInt())),
        canEdit = true,
    )

    private fun utc(year: Int, month: Int, day: Int): Instant =
        LocalDateTime(year, month, day, 0, 0).toInstant(TimeZone.UTC)
}
