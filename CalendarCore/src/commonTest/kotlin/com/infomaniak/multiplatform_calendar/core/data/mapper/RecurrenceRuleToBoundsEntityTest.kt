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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.Finite
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.Infinite
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind.FiniteDeferred
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RecurrenceRuleToBoundsEntityTest {

    // ---- Upper-bound kind (no UNTIL) ------------------------------------------------------------

    @Test
    fun infiniteRule_setsInfinite_andLowBoundOnly() {
        val timing = utcTiming()

        val bounds = RecurrenceRule(freq = Frequency.Daily).toRecurrenceBoundsEntity(timing)

        assertEquals(timing.dtStartInstantMs, bounds.firstOccurrenceInstantMs)
        assertEquals(Infinite, bounds.recurrenceBoundKind)
        assertNull(bounds.lastPossibleOccurrenceEndInstantMs)
        assertNull(bounds.lastOccurrenceEndLocalDateTime)
    }

    @Test
    fun countWithoutUntil_setsFiniteDeferred_andLeavesUpperBoundNull() {
        val timing = utcTiming()

        val bounds = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 200).toRecurrenceBoundsEntity(timing)

        assertEquals(timing.dtStartInstantMs, bounds.firstOccurrenceInstantMs)
        assertEquals(FiniteDeferred, bounds.recurrenceBoundKind)
        assertNull(bounds.lastPossibleOccurrenceEndInstantMs)
        assertNull(bounds.lastOccurrenceEndLocalDateTime)
    }

    // ---- Finite (UNTIL + duration, §7.24) -------------------------------------------------------

    @Test
    fun utcUntil_setsFinite_upperBoundIsUntilPlusDuration() {
        // 2h event, UNTIL bounds the last *start*, so the last *end* is UNTIL + 2h.
        val timing = utcTiming(start = LocalDateTime(2026, 1, 1, 10, 0), end = LocalDateTime(2026, 1, 1, 12, 0))
        val until = LocalDateTime(2026, 3, 31, 10, 0).toInstant(TimeZone.UTC)

        val bounds = RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateTimeUtc(until)).toRecurrenceBoundsEntity(timing)

        assertEquals(Finite, bounds.recurrenceBoundKind)
        assertEquals((until + 2.hours).toEpochMilliseconds(), bounds.lastPossibleOccurrenceEndInstantMs)
        assertNull(bounds.lastOccurrenceEndLocalDateTime)
    }

    @Test
    fun longEventUntil_upperBoundIncludesFullDuration_notJustUntil() {
        // 3-day event: a range at UNTIL + 2 days must still keep the series (overlap), §7.24/§7.37.
        val timing = utcTiming(start = LocalDateTime(2026, 1, 1, 9, 0), end = LocalDateTime(2026, 1, 4, 9, 0))
        val until = LocalDateTime(2026, 6, 1, 9, 0).toInstant(TimeZone.UTC)

        val bounds = RecurrenceRule(freq = Frequency.Weekly, until = RecurrenceUntil.DateTimeUtc(until)).toRecurrenceBoundsEntity(timing)

        assertEquals((until + 3.days).toEpochMilliseconds(), bounds.lastPossibleOccurrenceEndInstantMs)
    }

    @Test
    fun allDayUntil_anchorsUpperBoundAtUtcMidnightPlusDuration_paddedForDeviceZone() {
        // All-day 1-day event (DTEND exclusive next day → 1 day duration).
        val timing = allDayTiming(startDate = LocalDate(2026, 1, 1), endDate = LocalDate(2026, 1, 2))
        val until = LocalDate(2026, 1, 31)

        val bounds = RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateOnly(until)).toRecurrenceBoundsEntity(timing)

        assertEquals(Finite, bounds.recurrenceBoundKind)
        // 31 Jan + 1 day (UTC midnight) padded 14 h later so negative-offset device zones aren't dropped.
        val expectedUpper = LocalDateTime(2026, 2, 1, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds() + MAX_UTC_OFFSET_MS
        assertEquals(expectedUpper, bounds.lastPossibleOccurrenceEndInstantMs)
        // Low bound padded 14 h earlier so positive-offset device zones aren't dropped either.
        val expectedLower = timing.dtStartInstantMs!! - MAX_UTC_OFFSET_MS
        assertEquals(expectedLower, bounds.firstOccurrenceInstantMs)
        assertNull(bounds.lastOccurrenceEndLocalDateTime)
    }

    @Test
    fun floatingUntil_fillsLocalUpperBound_andNullInstant() {
        val timing = floatingTiming(start = LocalDateTime(2026, 1, 1, 8, 0), end = LocalDateTime(2026, 1, 1, 9, 30))
        val until = LocalDateTime(2026, 5, 20, 8, 0)

        val bounds = RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.Floating(until)).toRecurrenceBoundsEntity(timing)

        assertEquals(Finite, bounds.recurrenceBoundKind)
        assertNull(bounds.firstOccurrenceInstantMs)
        assertNull(bounds.lastPossibleOccurrenceEndInstantMs)
        assertEquals(LocalDateTime(2026, 5, 20, 9, 30), bounds.lastOccurrenceEndLocalDateTime) // UNTIL + 1h30
    }

    @Test
    fun zeroDurationEvent_upperBoundEqualsUntilExactly() {
        // dtStart == dtEnd → 0-length instance: the last end is UNTIL itself, with no spurious +0/off-by-one.
        val instant = LocalDateTime(2026, 1, 1, 10, 0)
        val timing = utcTiming(start = instant, end = instant)
        val until = LocalDateTime(2026, 3, 31, 10, 0).toInstant(TimeZone.UTC)

        val bounds = RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateTimeUtc(until)).toRecurrenceBoundsEntity(timing)

        assertEquals(Finite, bounds.recurrenceBoundKind)
        assertEquals(until.toEpochMilliseconds(), bounds.lastPossibleOccurrenceEndInstantMs)
    }

    @Test
    fun untilBeforeDtStart_producesCoherentEmptyBound_withoutThrowing() {
        // Degenerate but RFC-valid RRULE (UNTIL earlier than DTSTART → empty recurrence set): the mapper must
        // not throw and must keep the bound coherent (upper < lower). The range query then matches nothing and
        // the expander yields zero occurrences — no negative/overflowing bound is produced.
        val timing = utcTiming(start = LocalDateTime(2026, 1, 1, 10, 0), end = LocalDateTime(2026, 1, 1, 11, 0))
        val until = LocalDateTime(2025, 12, 1, 10, 0).toInstant(TimeZone.UTC)

        val bounds = RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateTimeUtc(until)).toRecurrenceBoundsEntity(timing)

        assertEquals(Finite, bounds.recurrenceBoundKind)
        assertTrue(
            bounds.lastPossibleOccurrenceEndInstantMs!! < bounds.firstOccurrenceInstantMs!!,
            "an UNTIL before DTSTART must leave the upper bound below the low bound (empty series)",
        )
    }

    @Test
    fun rdateAfterUntil_extendsFiniteUpperBound() {
        val timing = utcTiming(start = LocalDateTime(2026, 1, 1, 9, 0), end = LocalDateTime(2026, 1, 1, 10, 0))
        val until = LocalDateTime(2026, 1, 5, 9, 0).toInstant(TimeZone.UTC)

        val bounds = toRecurrenceBoundsEntity(
            timing = timing,
            recurrenceRule = RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.DateTimeUtc(until)),
            rDates = listOf(
                IcalDateValue.Zoned(
                    LocalDateTime(2026, 1, 10, 9, 0).toInstant(TimeZone.UTC),
                    TimeZone.UTC.id,
                ),
            ),
        )

        assertEquals(Finite, bounds?.recurrenceBoundKind)
        assertEquals(
            LocalDateTime(2026, 1, 10, 10, 0).toInstant(TimeZone.UTC).toEpochMilliseconds(),
            bounds?.lastPossibleOccurrenceEndInstantMs,
        )
    }

    @Test
    fun allDayRdateLowerBound_isPaddedOnEarlySide() {
        val timing = allDayTiming(startDate = LocalDate(2026, 1, 15), endDate = LocalDate(2026, 1, 16))
        val rdate = LocalDate(2026, 1, 10)

        val bounds = toRecurrenceBoundsEntity(
            timing = timing,
            recurrenceRule = null,
            rDates = listOf(IcalDateValue.AllDay(rdate)),
        )

        val expectedLower = LocalDateTime(rdate, MIDNIGHT).toInstant(TimeZone.UTC).toEpochMilliseconds() - MAX_UTC_OFFSET_MS
        assertEquals(expectedLower, bounds?.firstOccurrenceInstantMs)
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun utcTiming(
        start: LocalDateTime = LocalDateTime(2026, 1, 1, 10, 0),
        end: LocalDateTime = LocalDateTime(2026, 1, 1, 11, 0),
    ) = EventTimingEntity(
        dtStart = start,
        dtEnd = end,
        dtEndEffective = end,
        startTimeZone = "UTC",
        endTimeZone = "UTC",
        dtStartInstantMs = start.toInstant(TimeZone.UTC).toEpochMilliseconds(),
        dtEndInstantMs = end.toInstant(TimeZone.UTC).toEpochMilliseconds(),
        isAllDay = false,
    )

    private fun allDayTiming(startDate: LocalDate, endDate: LocalDate) = EventTimingEntity(
        dtStart = LocalDateTime(startDate, MIDNIGHT),
        dtEnd = LocalDateTime(endDate, MIDNIGHT),
        dtEndEffective = LocalDateTime(endDate, MIDNIGHT),
        startTimeZone = null,
        endTimeZone = null,
        dtStartInstantMs = LocalDateTime(startDate, MIDNIGHT).toInstant(TimeZone.UTC).toEpochMilliseconds(),
        dtEndInstantMs = LocalDateTime(endDate, MIDNIGHT).toInstant(TimeZone.UTC).toEpochMilliseconds(),
        isAllDay = true,
    )

    private fun floatingTiming(start: LocalDateTime, end: LocalDateTime) = EventTimingEntity(
        dtStart = start,
        dtEnd = end,
        dtEndEffective = end,
        startTimeZone = null,
        endTimeZone = null,
        dtStartInstantMs = null,
        dtEndInstantMs = null,
        isAllDay = false,
    )

    private companion object {
        val MIDNIGHT = kotlinx.datetime.LocalTime(0, 0)

        // Mirrors the mapper's private padding (widest IANA UTC offset magnitude).
        val MAX_UTC_OFFSET_MS = 14.hours.inWholeMilliseconds
    }
}
