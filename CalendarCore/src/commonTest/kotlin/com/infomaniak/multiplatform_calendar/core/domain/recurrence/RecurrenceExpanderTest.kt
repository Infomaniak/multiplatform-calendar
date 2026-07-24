/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.core.domain.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.WeekDayNum
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome.Completed
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class RecurrenceExpanderTest {

    private val paris = TimeZone.of("Europe/Paris")
    private val newYork = TimeZone.of("America/New_York")
    private val utc = TimeZone.UTC

    private fun ldt(text: String) = LocalDateTime.parse(text)

    private fun timedMaster(
        start: String,
        end: String,
        zone: TimeZone? = utc,
    ) = EventTiming(
        start = ldt(start),
        end = ldt(end),
        startTimeZone = zone,
        endTimeZone = zone,
        isAllDay = false,
    )

    private fun allDayMaster(startDate: String, spanDays: Int = 1) = EventTiming(
        start = LocalDateTime(LocalDate.parse(startDate), LocalDateTime.parse("2000-01-01T00:00").time),
        end = LocalDateTime(LocalDate.parse(startDate).plus(spanDaysPeriod(spanDays)), LocalDateTime.parse("2000-01-01T00:00").time),
        startTimeZone = null,
        endTimeZone = null,
        isAllDay = true,
    )

    private fun spanDaysPeriod(days: Int) = kotlinx.datetime.DatePeriod(days = days)

    private suspend fun expand(
        master: EventTiming,
        rule: RecurrenceRule,
        windowStart: Instant,
        windowEnd: Instant,
        defaultZone: TimeZone = utc,
        limits: ExpansionLimits = ExpansionLimits(),
    ): Pair<List<Occurrence>, ExpansionOutcome> {
        val out = mutableListOf<Occurrence>()
        val outcome = RecurrenceExpander.expandInto(out, master, rule, windowStart, windowEnd, defaultZone, limits)
        return out to outcome
    }

    private fun instant(text: String, zone: TimeZone = utc): Instant = LocalDateTime.parse(text).toInstant(zone)

    private fun days(vararg daysOfWeek: DayOfWeek): List<WeekDayNum> = daysOfWeek.map { WeekDayNum(dayOfWeek = it) }

    // region core FREQ / INTERVAL / COUNT / UNTIL

    @Test
    fun dailyCountEmitsExactlyCountInstances() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(ExpansionOutcome.Completed, outcome)
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-01-02T09:00"), ldt("2024-01-03T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun dtstartIsAlwaysInstanceOne() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 1),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(1, occ.size)
        assertEquals(ldt("2024-01-01T09:00"), occ.single().start)
    }

    @Test
    fun intervalSkipsPeriods() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, interval = 2, occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-01-03T09:00"), ldt("2024-01-05T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun untilIsInclusive() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateTimeUtc(instant("2024-01-03T09:00"))),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(ExpansionOutcome.Completed, outcome)
        assertEquals(3, occ.size)
        assertEquals(ldt("2024-01-03T09:00"), occ.last().start)
    }

    @Test
    fun untilDtstartEqualEmitsSingleInstance() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateTimeUtc(instant("2024-01-01T09:00"))),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(1, occ.size)
    }

    @Test
    fun countAndUntilTogetherStopAtCountWhenCountIsMoreRestrictive() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(
                freq = Frequency.Daily,
                occurrenceCount = 3,
                until = RecurrenceUntil.DateTimeUtc(instant("2024-12-31T09:00")),
            ),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2025-01-01T00:00"),
        )
        assertEquals(ExpansionOutcome.Completed, outcome)
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-01-02T09:00"), ldt("2024-01-03T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun countAndUntilTogetherStopAtUntilWhenUntilIsMoreRestrictive() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(
                freq = Frequency.Daily,
                occurrenceCount = 100,
                until = RecurrenceUntil.DateTimeUtc(instant("2024-01-03T09:00")),
            ),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2025-01-01T00:00"),
        )
        assertEquals(ExpansionOutcome.Completed, outcome)
        assertEquals(3, occ.size)
        assertEquals(ldt("2024-01-03T09:00"), occ.last().start)
    }

    @Test
    fun largeIntervalSkipsTheRightNumberOfPeriods() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, interval = 100, occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2025-06-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-04-10T09:00"), ldt("2024-07-19T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun dateOnlyUntilBoundsAnAllDaySeriesInclusively() = runTest {
        val master = allDayMaster("2024-01-01")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateOnly(LocalDate.parse("2024-01-03"))),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(ExpansionOutcome.Completed, outcome)
        assertEquals(
            listOf(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-02"), LocalDate.parse("2024-01-03")),
            occ.map { it.start.date },
        )
    }

    @Test
    fun floatingUntilIsComparedInWallClockNotAbsoluteInstant() = runTest {
        // A floating UNTIL has no absolute instant, so it is compared to the local start regardless of zone.
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00", zone = paris)
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.Floating(ldt("2024-01-02T09:00"))),
            windowStart = instant("2024-01-01T00:00", paris),
            windowEnd = instant("2024-02-01T00:00", paris),
        )
        assertEquals(ExpansionOutcome.Completed, outcome)
        assertEquals(listOf(ldt("2024-01-01T09:00"), ldt("2024-01-02T09:00")), occ.map { it.start })
    }

    // endregion

    // region sub-daily FREQ

    @Test
    fun hourlyStepsEveryIntervalHours() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T09:30")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Hourly, interval = 6, occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-01-02T12:00"),
        )
        assertEquals(
            listOf(
                ldt("2024-01-01T09:00"),
                ldt("2024-01-01T15:00"),
                ldt("2024-01-01T21:00"),
                ldt("2024-01-02T03:00"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun secondlyStepsEverySecond() = runTest {
        val master = timedMaster("2024-01-01T09:00:00", "2024-01-01T09:00:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Secondly, occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00:00"),
            windowEnd = instant("2024-01-02T00:00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-01T09:00:00"), ldt("2024-01-01T09:00:01"), ldt("2024-01-01T09:00:02")),
            occ.map { it.start },
        )
    }

    // endregion

    // region iterator invariants

    @Test
    fun expansionIsStrictlyIncreasingAndDuplicateFree() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Weekly, byDay = days(MONDAY, WEDNESDAY, FRIDAY), occurrenceCount = 30),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2025-01-01T00:00"),
        )
        val starts = occ.map { it.start }
        assertEquals(30, starts.size)
        assertEquals(starts.sorted(), starts) // monotonic non-decreasing
        assertEquals(starts.distinct(), starts) // no duplicates -> strictly increasing
    }

    // endregion

    // region window overlap

    @Test
    fun onlyInstancesOverlappingWindowAreEmitted() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily),
            windowStart = instant("2024-01-10T00:00"),
            windowEnd = instant("2024-01-13T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-10T09:00"), ldt("2024-01-11T09:00"), ldt("2024-01-12T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun longEventStraddlingWindowStartIsEmitted() = runTest {
        // 3-day event; instance starts before the window but overlaps into it.
        val master = timedMaster("2024-01-01T09:00", "2024-01-04T09:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 4),
            windowStart = instant("2024-01-03T00:00"),
            windowEnd = instant("2024-01-04T00:00"),
        )
        assertEquals(1, occ.size)
        assertEquals(ldt("2024-01-01T09:00"), occ.single().start)
    }

    // endregion

    // region monthly / yearly skip-invalid

    @Test
    fun monthlyOn31stSkipsShortMonths() = runTest {
        val master = timedMaster("2024-01-31T09:00", "2024-01-31T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2025-01-01T00:00"),
        )
        // Jan, Mar, May, Jul (Feb/Apr/Jun skipped — no 31st).
        assertEquals(
            listOf(ldt("2024-01-31T09:00"), ldt("2024-03-31T09:00"), ldt("2024-05-31T09:00"), ldt("2024-07-31T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun yearlyOnFeb29SkipsNonLeapYears() = runTest {
        val master = timedMaster("2024-02-29T09:00", "2024-02-29T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Yearly, occurrenceCount = 2),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2035-01-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-02-29T09:00"), ldt("2028-02-29T09:00")),
            occ.map { it.start },
        )
    }

    // endregion

    // region DST (Europe/Paris)

    @Test
    fun dailyPreservesWallClockAcrossSpringForward() = runTest {
        // Paris DST gap night 2024-03-31 (02:00 → 03:00). 09:00 must remain 09:00 every day.
        val master = timedMaster("2024-03-30T09:00", "2024-03-30T10:00", zone = paris)
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
            windowStart = instant("2024-03-30T00:00", paris),
            windowEnd = instant("2024-04-05T00:00", paris),
            defaultZone = paris,
        )
        assertEquals(
            listOf(ldt("2024-03-30T09:00"), ldt("2024-03-31T09:00"), ldt("2024-04-01T09:00")),
            occ.map { it.start },
        )
    }

    // endregion

    // region caps

    @Test
    fun occurrenceCapTruncates() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2100-01-01T00:00"),
            limits = ExpansionLimits(maxGeneratedOccurrences = 5),
        )
        assertEquals(5, occ.size)
        assertEquals(ExpansionOutcome.TruncatedByOccurrenceCap, outcome)
    }

    @Test
    fun degenerateRuleProducingNoDateStopsOnConsecutiveEmptyPeriodsInsteadOfLooping() = runTest {
        // Feb 31 never exists: only the forced DTSTART is emitted, then the empty streak trips the cap.
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Yearly, byMonth = listOf(2), byMonthDay = listOf(31)),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2999-01-01T00:00"),
            limits = ExpansionLimits(maxScannedPeriods = 50),
        )
        assertEquals(listOf(ldt("2024-01-01T09:00")), occ.map { it.start })
        assertEquals(ExpansionOutcome.StoppedByConsecutiveEmptyPeriods, outcome)
    }

    @Test
    fun expansionHonorsCooperativeCancellation() = runTest {
        val master = timedMaster("2024-01-01T00:00:00", "2024-01-01T00:00:01")
        val job = Job()
        assertFailsWith<CancellationException> {
            withContext(job) {
                job.cancel() // cancel from within the active context so the loop's ensureActive() observes it
                expand(
                    master,
                    RecurrenceRule(freq = Frequency.Secondly),
                    windowStart = instant("2024-01-01T00:00:00"),
                    windowEnd = instant("2999-01-01T00:00:00"),
                    limits = ExpansionLimits(maxGeneratedOccurrences = Int.MAX_VALUE, maxScannedPeriods = Int.MAX_VALUE),
                )
            }
        }
    }

    @Test
    fun expandsAHundredMastersOverALeapYearWindow() = runTest {
        // Exercises the fast-forward path (masters start years before the window) at volume; 2024 has 366 days.
        val windowStart = instant("2024-01-01T00:00")
        val windowEnd = instant("2025-01-01T00:00")
        var total = 0
        repeat(100) {
            val master = timedMaster("2020-01-01T09:00", "2020-01-01T10:00")
            val out = mutableListOf<Occurrence>()
            RecurrenceExpander.expandInto(out, master, RecurrenceRule(freq = Frequency.Daily), windowStart, windowEnd, utc)
            total += out.size
        }
        assertEquals(100 * 366, total)
    }

    // endregion

    // region all-day

    @Test
    fun allDayDailyPreservesDateSpan() = runTest {
        val master = allDayMaster("2024-01-01", spanDays = 1)
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 2),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(2, occ.size)
        assertTrue(occ.all { it.isAllDay })
        assertEquals(LocalDate.parse("2024-01-01"), occ.first().start.date)
        assertEquals(LocalDate.parse("2024-01-02"), occ.first().end.date)
    }

    // endregion

    // region BY* : BYMONTH / BYMONTHDAY / BYDAY

    @Test
    fun weeklyByDayExpandsWithinWeek() = runTest {
        // DTSTART is a Monday; MO,WE,FR → 3 per week.
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Weekly, byDay = days(MONDAY, WEDNESDAY, FRIDAY), occurrenceCount = 5),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(
            listOf(
                ldt("2024-01-01T09:00"), ldt("2024-01-03T09:00"), ldt("2024-01-05T09:00"),
                ldt("2024-01-08T09:00"), ldt("2024-01-10T09:00"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun dailyByDayLimitsToMatchingWeekdays() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00") // Monday
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, byDay = days(SATURDAY, SUNDAY)),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-01-15T00:00"),
        )
        assertEquals(
            // Jan 1 (Monday) is DTSTART, forced in at position 1 (§7.8); then the Sat/Sun instances.
            listOf(
                ldt("2024-01-01T09:00"),
                ldt("2024-01-06T09:00"), ldt("2024-01-07T09:00"),
                ldt("2024-01-13T09:00"), ldt("2024-01-14T09:00"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun monthlyByDayFirstMondayUsesOrdinal() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byDay = listOf(WeekDayNum(1, MONDAY)), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-06-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-02-05T09:00"), ldt("2024-03-04T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun monthlyByDayLastFridayUsesNegativeOrdinal() = runTest {
        val master = timedMaster("2024-01-26T09:00", "2024-01-26T10:00") // last Friday of Jan 2024
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byDay = listOf(WeekDayNum(-1, FRIDAY)), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-06-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-26T09:00"), ldt("2024-02-23T09:00"), ldt("2024-03-29T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun monthlyByMonthDayExpandsMultipleDays() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byMonthDay = listOf(1, 15), occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-06-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-01-15T09:00"), ldt("2024-02-01T09:00"), ldt("2024-02-15T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun monthlyNegativeByMonthDayIsLastDay() = runTest {
        val master = timedMaster("2024-01-31T09:00", "2024-01-31T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byMonthDay = listOf(-1), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-06-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-31T09:00"), ldt("2024-02-29T09:00"), ldt("2024-03-31T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun monthlyByMonthDayAndByDayIntersect() = runTest {
        // The 13th, only when it is a Friday.
        val master = timedMaster("2024-09-13T09:00", "2024-09-13T10:00") // Fri 13 Sep 2024
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byMonthDay = listOf(13), byDay = days(FRIDAY), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2026-01-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-09-13T09:00"), ldt("2024-12-13T09:00"), ldt("2025-06-13T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun yearlyByMonthExpandsToEachListedMonth() = runTest {
        val master = timedMaster("2024-01-10T09:00", "2024-01-10T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Yearly, byMonth = listOf(1, 6, 12), occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2026-01-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-10T09:00"), ldt("2024-06-10T09:00"), ldt("2024-12-10T09:00"), ldt("2025-01-10T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun yearlyByDayWithoutByMonthExpandsAcrossYear() = runTest {
        // Every Monday of the year → first three Mondays of 2024.
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00") // Monday
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Yearly, byDay = days(MONDAY), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2025-01-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-01-08T09:00"), ldt("2024-01-15T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun dtStartIncludedEvenWhenNonConforming() = runTest {
        // DTSTART is the 10th but the rule only lists the 1st and 15th: DTSTART still comes first.
        val master = timedMaster("2024-01-10T09:00", "2024-01-10T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byMonthDay = listOf(1, 15), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-06-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-10T09:00"), ldt("2024-01-15T09:00"), ldt("2024-02-01T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun preDtStartCandidatesInFirstPeriodAreDropped() = runTest {
        // DTSTART on the 20th; the 1st of the same month must not be emitted.
        val master = timedMaster("2024-01-20T09:00", "2024-01-20T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byMonthDay = listOf(1), occurrenceCount = 2),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-06-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-20T09:00"), ldt("2024-02-01T09:00")),
            occ.map { it.start },
        )
    }

    // endregion

    // region sub-daily BYHOUR / BYMINUTE / BYSECOND

    @Test
    fun dailyByHourExpandsWithinDay() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, byHour = listOf(9, 14), occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(
            listOf(
                ldt("2024-01-01T09:00"), ldt("2024-01-01T14:00"),
                ldt("2024-01-02T09:00"), ldt("2024-01-02T14:00"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun dailyByHourAndByMinuteFormCartesianProduct() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T09:30")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, byHour = listOf(9, 10), byMinute = listOf(0, 30), occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-02-01T00:00"),
        )
        assertEquals(
            listOf(
                ldt("2024-01-01T09:00"), ldt("2024-01-01T09:30"),
                ldt("2024-01-01T10:00"), ldt("2024-01-01T10:30"),
            ),
            occ.map { it.start },
        )
    }

    // endregion

    // region BYYEARDAY / BYWEEKNO

    @Test
    fun yearlyByYearDayNegativeSelectsLastDayOfYear() = runTest {
        val master = timedMaster("2024-01-01T08:00", "2024-01-01T09:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Yearly, byYearDay = listOf(-1), occurrenceCount = 2),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2026-06-01T00:00"),
        )
        // DTSTART is always emitted first (§7.8), then the conforming last-day-of-year instances.
        assertEquals(listOf(ldt("2024-01-01T08:00"), ldt("2024-12-31T08:00")), occ.map { it.start })
    }

    @Test
    fun yearlyByWeekNumberWithByDaySelectsMondayOfWeekOne() = runTest {
        val master = timedMaster("2024-01-01T08:00", "2024-01-01T09:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Yearly, byWeekNumber = listOf(1), byDay = days(DayOfWeek.MONDAY), occurrenceCount = 3),
            windowStart = instant("2023-01-01T00:00"),
            windowEnd = instant("2027-06-01T00:00"),
        )
        // ISO week 1 Mondays: 2024-01-01, 2024-12-30 (week 1 of 2025), 2025-12-29 (week 1 of 2026).
        assertEquals(
            listOf(ldt("2024-01-01T08:00"), ldt("2024-12-30T08:00"), ldt("2025-12-29T08:00")),
            occ.map { it.start },
        )
    }

    // endregion

    // region BYSETPOS

    @Test
    fun monthlyBySetPosSelectsLastWeekdayOfMonth() = runTest {
        val master = timedMaster("2024-01-31T10:00", "2024-01-31T11:00")
        val weekdays = days(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Monthly, byDay = weekdays, byOccurrencePosition = listOf(-1), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-05-01T00:00"),
        )
        assertEquals(
            listOf(ldt("2024-01-31T10:00"), ldt("2024-02-29T10:00"), ldt("2024-03-29T10:00")),
            occ.map { it.start },
        )
    }

    // endregion

    // region WKST alignment

    @Test
    fun weeklyIntervalTwoWithWeekStartMonday() = runTest {
        val master = timedMaster("1997-08-05T09:00", "1997-08-05T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(
                freq = Frequency.Weekly,
                interval = 2,
                byDay = days(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
                weekStart = DayOfWeek.MONDAY,
                occurrenceCount = 4,
            ),
            windowStart = instant("1997-08-01T00:00"),
            windowEnd = instant("1997-09-01T00:00"),
        )
        assertEquals(
            listOf(ldt("1997-08-05T09:00"), ldt("1997-08-10T09:00"), ldt("1997-08-19T09:00"), ldt("1997-08-24T09:00")),
            occ.map { it.start },
        )
    }

    @Test
    fun weeklyIntervalTwoWithWeekStartSundayShiftsBoundary() = runTest {
        val master = timedMaster("1997-08-05T09:00", "1997-08-05T10:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(
                freq = Frequency.Weekly,
                interval = 2,
                byDay = days(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
                weekStart = DayOfWeek.SUNDAY,
                occurrenceCount = 4,
            ),
            windowStart = instant("1997-08-01T00:00"),
            windowEnd = instant("1997-09-01T00:00"),
        )
        assertEquals(
            listOf(ldt("1997-08-05T09:00"), ldt("1997-08-17T09:00"), ldt("1997-08-19T09:00"), ldt("1997-08-31T09:00")),
            occ.map { it.start },
        )
    }

    // endregion

    // region sub-daily BY* expand/limit

    @Test
    fun hourlyByMinuteExpandsWithinEachHour() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T09:15")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Hourly, byMinute = listOf(0, 30), occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-01-02T00:00"),
        )
        assertEquals(
            listOf(
                ldt("2024-01-01T09:00"), ldt("2024-01-01T09:30"),
                ldt("2024-01-01T10:00"), ldt("2024-01-01T10:30"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun minutelyBySecondExpandsWithinEachMinute() = runTest {
        val master = timedMaster("2024-01-01T09:00:00", "2024-01-01T09:01:00")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Minutely, bySecond = listOf(0, 30), occurrenceCount = 4),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-01-02T00:00"),
        )
        assertEquals(
            listOf(
                ldt("2024-01-01T09:00:00"), ldt("2024-01-01T09:00:30"),
                ldt("2024-01-01T09:01:00"), ldt("2024-01-01T09:01:30"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun hourlyByHourLimitsWhichHoursCount() = runTest {
        val master = timedMaster("2024-01-01T09:00", "2024-01-01T09:15")
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Hourly, byHour = listOf(9, 12), occurrenceCount = 3),
            windowStart = instant("2024-01-01T00:00"),
            windowEnd = instant("2024-01-03T00:00"),
        )
        // 09:00 (DTSTART), 12:00, then next day's 09:00.
        assertEquals(
            listOf(ldt("2024-01-01T09:00"), ldt("2024-01-01T12:00"), ldt("2024-01-02T09:00")),
            occ.map { it.start },
        )
    }

    // endregion

    // region DST gaps and dense far-starting series (PR 4c)

    @Test
    fun springForwardNonexistentInstanceIsSkippedAndNotEmitted() = runTest {
        // 2025-03-30 is the Europe/Paris spring-forward day: 02:00 jumps to 03:00, so 02:30 does not exist.
        val master = timedMaster("2025-03-28T02:30", "2025-03-28T03:00", zone = paris)
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily),
            windowStart = instant("2025-03-28T00:00", paris),
            windowEnd = instant("2025-04-01T00:00", paris),
        )
        assertEquals(
            listOf(ldt("2025-03-28T02:30"), ldt("2025-03-29T02:30"), ldt("2025-03-31T02:30")),
            occ.map { it.start },
        )
    }

    @Test
    fun boundedSeriesWhoseEveryInstanceIsADstGapCompletesInsteadOfScanningToTheCap() = runTest {
        // Every last Sunday of March at 02:30 in Europe/Paris is a spring-forward gap. Bounds must be
        // evaluated before the gap is skipped, otherwise UNTIL is never reached and expansion runs to the cap.
        val master = timedMaster("2020-03-29T02:30", "2020-03-29T03:30", zone = paris)
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(
                freq = Frequency.Yearly,
                byMonth = listOf(3),
                byDay = listOf(WeekDayNum(-1, SUNDAY)),
                until = RecurrenceUntil.Floating(ldt("2035-01-01T00:00")),
            ),
            windowStart = instant("2020-01-01T00:00", paris),
            windowEnd = instant("2035-01-01T00:00", paris),
            limits = ExpansionLimits(maxScannedPeriods = 50),
        )
        assertEquals(Completed, outcome)
        assertEquals(emptyList<LocalDateTime>(), occ.map { it.start })
    }

    @Test
    fun springForwardGapDoesNotConsumeCount() = runTest {
        // COUNT tallies only existing instances: the skipped 03-30 must not count, so the 4th lands on 04-01.
        val master = timedMaster("2025-03-28T02:30", "2025-03-28T03:00", zone = paris)
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 4),
            windowStart = instant("2025-03-01T00:00", paris),
            windowEnd = instant("2025-05-01T00:00", paris),
        )
        assertEquals(Completed, outcome)
        assertEquals(
            listOf(
                ldt("2025-03-28T02:30"),
                ldt("2025-03-29T02:30"),
                ldt("2025-03-31T02:30"),
                ldt("2025-04-01T02:30"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun fallBackAmbiguousInstanceIsKeptOnce() = runTest {
        // 2025-10-26 is the Europe/Paris fall-back day: 02:30 occurs twice but still exists, so it is emitted.
        val master = timedMaster("2025-10-25T02:30", "2025-10-25T03:00", zone = paris)
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily),
            windowStart = instant("2025-10-25T00:00", paris),
            windowEnd = instant("2025-10-28T00:00", paris),
        )
        assertEquals(
            listOf(ldt("2025-10-25T02:30"), ldt("2025-10-26T02:30"), ldt("2025-10-27T02:30")),
            occ.map { it.start },
        )
    }

    @Test
    fun denseSeriesStartingLongBeforeTheWindowFastForwardsInsteadOfScanning() = runTest {
        // A years-old FREQ=SECONDLY reaching a tiny window must not step through ~1.6e8 pre-window seconds.
        val master = timedMaster("2020-01-01T00:00:00", "2020-01-01T00:00:01")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Secondly),
            windowStart = instant("2025-01-01T00:00:00"),
            windowEnd = instant("2025-01-01T00:00:05"),
        )
        assertEquals(Completed, outcome)
        assertEquals(
            listOf(
                ldt("2025-01-01T00:00:00"),
                ldt("2025-01-01T00:00:01"),
                ldt("2025-01-01T00:00:02"),
                ldt("2025-01-01T00:00:03"),
                ldt("2025-01-01T00:00:04"),
            ),
            occ.map { it.start },
        )
    }

    @Test
    fun countedSeriesIsNotFastForwardedSoEveryInstanceStillCounts() = runTest {
        // With COUNT the fast-forward is disabled: the 3 counted instances are the first ones from DTSTART.
        val master = timedMaster("2020-01-01T00:00:00", "2020-01-01T00:00:10")
        val (occ, outcome) = expand(
            master,
            RecurrenceRule(freq = Frequency.Secondly, occurrenceCount = 3),
            windowStart = instant("2020-01-01T00:00:00"),
            windowEnd = instant("2020-01-02T00:00:00"),
        )
        assertEquals(Completed, outcome)
        assertEquals(
            listOf(
                ldt("2020-01-01T00:00:00"),
                ldt("2020-01-01T00:00:01"),
                ldt("2020-01-01T00:00:02"),
            ),
            occ.map { it.start },
        )
    }

    // endregion

    // region RFC 5545 §3.8.5.3 canonical recurrence vectors

    private fun ny(start: String) = timedMaster(start, start, zone = newYork)

    private suspend fun starts(master: EventTiming, rule: RecurrenceRule): List<LocalDateTime> =
        expand(master, rule, instant("1996-01-01T00:00", newYork), instant("2010-01-01T00:00", newYork)).first.map { it.start }

    private fun weekDay(ordinal: Int, dayOfWeek: DayOfWeek) = WeekDayNum(ordinal = ordinal, dayOfWeek = dayOfWeek)

    @Test
    fun rfcDailyForTenOccurrences() = runTest {
        val result = starts(ny("1997-09-02T09:00"), RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 10))
        assertEquals(
            listOf(
                "1997-09-02T09:00", "1997-09-03T09:00", "1997-09-04T09:00", "1997-09-05T09:00", "1997-09-06T09:00",
                "1997-09-07T09:00", "1997-09-08T09:00", "1997-09-09T09:00", "1997-09-10T09:00", "1997-09-11T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryOtherDayForFiveOccurrences() = runTest {
        val result = starts(ny("1997-09-02T09:00"), RecurrenceRule(freq = Frequency.Daily, interval = 2, occurrenceCount = 5))
        assertEquals(listOf("02", "04", "06", "08", "10").map { ldt("1997-09-${it}T09:00") }, result)
    }

    @Test
    fun rfcEveryTenDaysFiveOccurrences() = runTest {
        val result = starts(ny("1997-09-02T09:00"), RecurrenceRule(freq = Frequency.Daily, interval = 10, occurrenceCount = 5))
        assertEquals(
            listOf("1997-09-02T09:00", "1997-09-12T09:00", "1997-09-22T09:00", "1997-10-02T09:00", "1997-10-12T09:00").map(::ldt),
            result,
        )
    }

    @Test
    fun rfcWeeklyForTenOccurrences() = runTest {
        val result = starts(ny("1997-09-02T09:00"), RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 10))
        assertEquals(
            listOf(
                "1997-09-02T09:00", "1997-09-09T09:00", "1997-09-16T09:00", "1997-09-23T09:00", "1997-09-30T09:00",
                "1997-10-07T09:00", "1997-10-14T09:00", "1997-10-21T09:00", "1997-10-28T09:00", "1997-11-04T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcWeeklyOnTuesdayAndThursdayForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-09-02T09:00"),
            RecurrenceRule(freq = Frequency.Weekly, occurrenceCount = 10, weekStart = SUNDAY, byDay = days(TUESDAY, THURSDAY)),
        )
        assertEquals(
            listOf(
                "1997-09-02T09:00", "1997-09-04T09:00", "1997-09-09T09:00", "1997-09-11T09:00", "1997-09-16T09:00",
                "1997-09-18T09:00", "1997-09-23T09:00", "1997-09-25T09:00", "1997-09-30T09:00", "1997-10-02T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryOtherWeekOnMondayWednesdayFriday() = runTest {
        val result = starts(
            ny("1997-09-02T09:00"),
            RecurrenceRule(
                freq = Frequency.Weekly,
                interval = 2,
                occurrenceCount = 9,
                weekStart = SUNDAY,
                byDay = days(MONDAY, WEDNESDAY, FRIDAY),
            ),
        )
        assertEquals(
            listOf(
                "1997-09-02T09:00", "1997-09-03T09:00", "1997-09-05T09:00", "1997-09-15T09:00", "1997-09-17T09:00",
                "1997-09-19T09:00", "1997-09-29T09:00", "1997-10-01T09:00", "1997-10-03T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcMonthlyOnFirstFridayForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-09-05T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, occurrenceCount = 10, byDay = listOf(weekDay(1, FRIDAY))),
        )
        assertEquals(
            listOf(
                "1997-09-05T09:00", "1997-10-03T09:00", "1997-11-07T09:00", "1997-12-05T09:00", "1998-01-02T09:00",
                "1998-02-06T09:00", "1998-03-06T09:00", "1998-04-03T09:00", "1998-05-01T09:00", "1998-06-05T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryOtherMonthOnFirstAndLastSunday() = runTest {
        val result = starts(
            ny("1997-09-07T09:00"),
            RecurrenceRule(
                freq = Frequency.Monthly,
                interval = 2,
                occurrenceCount = 10,
                byDay = listOf(weekDay(1, SUNDAY), weekDay(-1, SUNDAY)),
            ),
        )
        assertEquals(
            listOf(
                "1997-09-07T09:00", "1997-09-28T09:00", "1997-11-02T09:00", "1997-11-30T09:00", "1998-01-04T09:00",
                "1998-01-25T09:00", "1998-03-01T09:00", "1998-03-29T09:00", "1998-05-03T09:00", "1998-05-31T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcMonthlyOnSecondToLastMondayForSixOccurrences() = runTest {
        val result = starts(
            ny("1997-09-22T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, occurrenceCount = 6, byDay = listOf(weekDay(-2, MONDAY))),
        )
        assertEquals(
            listOf(
                "1997-09-22T09:00", "1997-10-20T09:00", "1997-11-17T09:00", "1997-12-22T09:00", "1998-01-19T09:00",
                "1998-02-16T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcMonthlyOnThirdToLastDay() = runTest {
        val result = starts(
            ny("1997-09-28T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, occurrenceCount = 4, byMonthDay = listOf(-3)),
        )
        assertEquals(
            listOf("1997-09-28T09:00", "1997-10-29T09:00", "1997-11-28T09:00", "1997-12-29T09:00").map(::ldt),
            result,
        )
    }

    @Test
    fun rfcMonthlyOnSecondAndFifteenthForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-09-02T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, occurrenceCount = 10, byMonthDay = listOf(2, 15)),
        )
        assertEquals(
            listOf(
                "1997-09-02T09:00", "1997-09-15T09:00", "1997-10-02T09:00", "1997-10-15T09:00", "1997-11-02T09:00",
                "1997-11-15T09:00", "1997-12-02T09:00", "1997-12-15T09:00", "1998-01-02T09:00", "1998-01-15T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcMonthlyOnFirstAndLastDayForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-09-30T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, occurrenceCount = 10, byMonthDay = listOf(1, -1)),
        )
        assertEquals(
            listOf(
                "1997-09-30T09:00", "1997-10-01T09:00", "1997-10-31T09:00", "1997-11-01T09:00", "1997-11-30T09:00",
                "1997-12-01T09:00", "1997-12-31T09:00", "1998-01-01T09:00", "1998-01-31T09:00", "1998-02-01T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryEighteenMonthsOnDaysTenToFifteenForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-09-10T09:00"),
            RecurrenceRule(
                freq = Frequency.Monthly,
                interval = 18,
                occurrenceCount = 10,
                byMonthDay = listOf(10, 11, 12, 13, 14, 15),
            ),
        )
        assertEquals(
            listOf(
                "1997-09-10T09:00", "1997-09-11T09:00", "1997-09-12T09:00", "1997-09-13T09:00", "1997-09-14T09:00",
                "1997-09-15T09:00", "1999-03-10T09:00", "1999-03-11T09:00", "1999-03-12T09:00", "1999-03-13T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryTuesdayEveryOtherMonth() = runTest {
        val result = starts(
            ny("1997-09-02T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, interval = 2, byDay = days(TUESDAY)),
        ).take(5)
        assertEquals(
            listOf("1997-09-02T09:00", "1997-09-09T09:00", "1997-09-16T09:00", "1997-09-23T09:00", "1997-09-30T09:00").map(::ldt),
            result,
        )
    }

    @Test
    fun rfcYearlyInJuneAndJulyForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-06-10T09:00"),
            RecurrenceRule(freq = Frequency.Yearly, occurrenceCount = 10, byMonth = listOf(6, 7)),
        )
        assertEquals(
            listOf(
                "1997-06-10T09:00", "1997-07-10T09:00", "1998-06-10T09:00", "1998-07-10T09:00", "1999-06-10T09:00",
                "1999-07-10T09:00", "2000-06-10T09:00", "2000-07-10T09:00", "2001-06-10T09:00", "2001-07-10T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryOtherYearInJanuaryFebruaryMarchForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-03-10T09:00"),
            RecurrenceRule(freq = Frequency.Yearly, interval = 2, occurrenceCount = 10, byMonth = listOf(1, 2, 3)),
        )
        assertEquals(
            listOf(
                "1997-03-10T09:00", "1999-01-10T09:00", "1999-02-10T09:00", "1999-03-10T09:00", "2001-01-10T09:00",
                "2001-02-10T09:00", "2001-03-10T09:00", "2003-01-10T09:00", "2003-02-10T09:00", "2003-03-10T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryThirdYearOnFirstHundredthAndTwoHundredthDayForTenOccurrences() = runTest {
        val result = starts(
            ny("1997-01-01T09:00"),
            RecurrenceRule(freq = Frequency.Yearly, interval = 3, occurrenceCount = 10, byYearDay = listOf(1, 100, 200)),
        )
        assertEquals(
            listOf(
                "1997-01-01T09:00", "1997-04-10T09:00", "1997-07-19T09:00", "2000-01-01T09:00", "2000-04-09T09:00",
                "2000-07-18T09:00", "2003-01-01T09:00", "2003-04-10T09:00", "2003-07-19T09:00", "2006-01-01T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryTwentiethMondayOfTheYear() = runTest {
        val result = starts(
            ny("1997-05-19T09:00"),
            RecurrenceRule(freq = Frequency.Yearly, byDay = listOf(weekDay(20, MONDAY))),
        ).take(3)
        assertEquals(listOf("1997-05-19T09:00", "1998-05-18T09:00", "1999-05-17T09:00").map(::ldt), result)
    }

    @Test
    fun rfcMondayOfWeekNumberTwenty() = runTest {
        val result = starts(
            ny("1997-05-12T09:00"),
            RecurrenceRule(freq = Frequency.Yearly, byWeekNumber = listOf(20), byDay = days(MONDAY)),
        ).take(3)
        assertEquals(listOf("1997-05-12T09:00", "1998-05-11T09:00", "1999-05-17T09:00").map(::ldt), result)
    }

    @Test
    fun rfcEveryThursdayInMarch() = runTest {
        val result = starts(
            ny("1997-03-13T09:00"),
            RecurrenceRule(freq = Frequency.Yearly, byMonth = listOf(3), byDay = days(THURSDAY)),
        ).take(7)
        assertEquals(
            listOf(
                "1997-03-13T09:00", "1997-03-20T09:00", "1997-03-27T09:00", "1998-03-05T09:00", "1998-03-12T09:00",
                "1998-03-19T09:00", "1998-03-26T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryThursdayInJuneJulyAugust() = runTest {
        val result = starts(
            ny("1997-06-05T09:00"),
            RecurrenceRule(freq = Frequency.Yearly, byDay = days(THURSDAY), byMonth = listOf(6, 7, 8)),
        ).take(5)
        assertEquals(
            listOf("1997-06-05T09:00", "1997-06-12T09:00", "1997-06-19T09:00", "1997-06-26T09:00", "1997-07-03T09:00").map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryFridayTheThirteenth() = runTest {
        // The RFC lists only matching dates; our §7.8 forces the non-matching DTSTART as instance #1, hence its lead.
        val result = starts(
            ny("1997-09-02T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, byDay = days(FRIDAY), byMonthDay = listOf(13)),
        ).take(6)
        assertEquals(
            listOf(
                "1997-09-02T09:00", "1998-02-13T09:00", "1998-03-13T09:00", "1998-11-13T09:00", "1999-08-13T09:00",
                "2000-10-13T09:00",
            ).map(::ldt),
            result,
        )
    }

    @Test
    fun rfcFirstSaturdayFollowingFirstSundayOfMonth() = runTest {
        val result = starts(
            ny("1997-09-13T09:00"),
            RecurrenceRule(freq = Frequency.Monthly, byDay = days(SATURDAY), byMonthDay = listOf(7, 8, 9, 10, 11, 12, 13)),
        ).take(4)
        assertEquals(
            listOf("1997-09-13T09:00", "1997-10-11T09:00", "1997-11-08T09:00", "1997-12-13T09:00").map(::ldt),
            result,
        )
    }

    @Test
    fun rfcEveryFourYearsUsPresidentialElectionDay() = runTest {
        val result = starts(
            ny("1996-11-05T09:00"),
            RecurrenceRule(
                freq = Frequency.Yearly,
                interval = 4,
                byMonth = listOf(11),
                byDay = days(TUESDAY),
                byMonthDay = listOf(2, 3, 4, 5, 6, 7, 8),
            ),
        ).take(3)
        assertEquals(listOf("1996-11-05T09:00", "2000-11-07T09:00", "2004-11-02T09:00").map(::ldt), result)
    }

    @Test
    fun rfcThirdInstanceOfWeekdaySetEachMonthViaBySetPos() = runTest {
        val result = starts(
            ny("1997-09-04T09:00"),
            RecurrenceRule(
                freq = Frequency.Monthly,
                occurrenceCount = 3,
                byDay = days(TUESDAY, WEDNESDAY, THURSDAY),
                byOccurrencePosition = listOf(3),
            ),
        )
        assertEquals(listOf("1997-09-04T09:00", "1997-10-07T09:00", "1997-11-06T09:00").map(::ldt), result)
    }

    @Test
    fun rfcSecondToLastWeekdayOfMonthViaBySetPos() = runTest {
        val result = starts(
            ny("1997-09-29T09:00"),
            RecurrenceRule(
                freq = Frequency.Monthly,
                byDay = days(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
                byOccurrencePosition = listOf(-2),
            ),
        ).take(3)
        assertEquals(listOf("1997-09-29T09:00", "1997-10-30T09:00", "1997-11-27T09:00").map(::ldt), result)
    }

    @Test
    fun fallBackAmbiguousInstanceResolvesDeterministicallyToTheEarlierOffset() = runTest {
        // 2025-10-26T02:30 Paris exists twice (CEST then CET); the round-trip pins it to the earlier CEST instant.
        val master = timedMaster("2025-10-26T02:30", "2025-10-26T03:00", zone = paris)
        val (occ, _) = expand(
            master,
            RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 1),
            windowStart = instant("2025-10-26T00:00", paris),
            windowEnd = instant("2025-10-27T00:00", paris),
        )
        assertEquals(instant("2025-10-26T00:30", utc), occ.single().start.toInstant(paris))
    }

    // endregion
}
