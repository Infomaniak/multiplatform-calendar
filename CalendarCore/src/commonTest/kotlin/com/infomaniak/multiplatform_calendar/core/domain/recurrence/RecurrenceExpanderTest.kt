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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrenceExpanderTest {

    private val paris = TimeZone.of("Europe/Paris")
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
}
