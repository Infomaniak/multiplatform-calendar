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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceRuleParserTest {

    private fun parseSupported(rule: String): RecurrenceRule {
        val result = RecurrenceRuleParser.parse(rule)
        assertTrue(result is RecurrenceRuleParseResult.Supported, "Expected Supported for '$rule' but was $result")
        return result.rule
    }

    private fun assertFailed(rule: String, reason: RecurrenceRuleFailureReason) {
        val result = RecurrenceRuleParser.parse(rule)
        assertTrue(result is RecurrenceRuleParseResult.Failed, "Expected Failed for '$rule' but was $result")
        assertEquals(reason, result.reason)
    }

    @Test
    fun parsesDailyFrequency() {
        assertEquals(Frequency.Daily, parseSupported("FREQ=DAILY").freq)
    }

    @Test
    fun stripsRrulePrefix() {
        assertEquals(Frequency.Weekly, parseSupported("RRULE:FREQ=WEEKLY").freq)
    }

    @Test
    fun parsesIntervalCountAndByMonth() {
        val rule = parseSupported("FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYMONTH=1,6,12")
        assertEquals(2, rule.interval)
        assertEquals(10, rule.occurrenceCount)
        assertEquals(listOf(1, 6, 12), rule.byMonth)
    }

    @Test
    fun parsesByDayWithOrdinals() {
        val rule = parseSupported("FREQ=MONTHLY;BYDAY=2MO,-1FR")
        assertEquals(
            listOf(
                WeekDayNum(ordinal = 2, dayOfWeek = DayOfWeek.MONDAY),
                WeekDayNum(ordinal = -1, dayOfWeek = DayOfWeek.FRIDAY),
            ),
            rule.byDay,
        )
    }

    @Test
    fun parsesRareByRules() {
        val rule = parseSupported("FREQ=YEARLY;BYWEEKNO=1,53;BYYEARDAY=1,-1;BYHOUR=8,20;BYMINUTE=0,30;BYSECOND=0,59")
        assertEquals(listOf(1, 53), rule.byWeekNumber)
        assertEquals(listOf(1, -1), rule.byYearDay)
        assertEquals(listOf(8, 20), rule.byHour)
        assertEquals(listOf(0, 30), rule.byMinute)
        assertEquals(listOf(0, 59), rule.bySecond)
    }

    @Test
    fun parsesWeekStart() {
        assertEquals(DayOfWeek.SUNDAY, parseSupported("FREQ=WEEKLY;WKST=SU").weekStart)
    }

    @Test
    fun failsOnUnknownExtensionTokens() {
        assertFailed("FREQ=DAILY;X-CUSTOM=whatever", RecurrenceRuleFailureReason.MalformedGrammar)
    }

    @Test
    fun failsOnMultipleRuleLines() {
        assertFailed("FREQ=DAILY\nFREQ=WEEKLY", RecurrenceRuleFailureReason.MalformedGrammar)
    }

    @Test
    fun failsOnDuplicateRuleParts() {
        assertFailed("FREQ=DAILY;FREQ=WEEKLY", RecurrenceRuleFailureReason.MalformedGrammar)
    }

    @Test
    fun failsOnEmptyRuleParts() {
        assertFailed("FREQ=DAILY;;COUNT=2", RecurrenceRuleFailureReason.MalformedGrammar)
    }

    @Test
    fun failsWhenByMonthDayWithWeeklyFrequency() {
        assertFailed("FREQ=WEEKLY;BYMONTHDAY=1", RecurrenceRuleFailureReason.InvalidByMonthDayForFrequency)
    }

    @Test
    fun failsWhenByYearDayWithDailyFrequency() {
        assertFailed("FREQ=DAILY;BYYEARDAY=1", RecurrenceRuleFailureReason.InvalidByYearDayFrequency)
    }

    @Test
    fun failsWhenBySetPosWithoutAnotherByRule() {
        assertFailed("FREQ=MONTHLY;BYSETPOS=1", RecurrenceRuleFailureReason.BySetPosWithoutByRule)
    }

    @Test
    fun failsWhenFrequencyMissing() {
        assertFailed("INTERVAL=2", RecurrenceRuleFailureReason.MissingFrequency)
    }

    @Test
    fun failsWhenCountAndUntilTogether() {
        assertFailed("FREQ=DAILY;COUNT=5;UNTIL=20260101T000000Z", RecurrenceRuleFailureReason.CountAndUntilTogether)
    }

    @Test
    fun failsWhenByDayOrdinalWithDailyFrequency() {
        assertFailed("FREQ=DAILY;BYDAY=2MO", RecurrenceRuleFailureReason.InvalidByDayOrdinalForFrequency)
    }

    @Test
    fun failsWhenByYearDayWithMonthlyFrequency() {
        assertFailed("FREQ=MONTHLY;BYYEARDAY=100", RecurrenceRuleFailureReason.InvalidByYearDayFrequency)
    }

    @Test
    fun failsWhenByWeekNoWithNonYearlyFrequency() {
        assertFailed("FREQ=MONTHLY;BYWEEKNO=5", RecurrenceRuleFailureReason.InvalidByWeekNoFrequency)
    }

    @Test
    fun failsOnLeapSecond() {
        assertFailed("FREQ=DAILY;BYSECOND=60", RecurrenceRuleFailureReason.LeapSecondUnsupported)
    }

    @Test
    fun failsOnRscale() {
        assertFailed("FREQ=YEARLY;RSCALE=HEBREW", RecurrenceRuleFailureReason.UnsupportedRscale)
    }

    @Test
    fun failsOnMalformedGrammarWithoutThrowing() {
        assertFailed("this is not a rule", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=DAILY;BYMONTH=13", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=DAILY;BYMONTHDAY=0", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("", RecurrenceRuleFailureReason.MalformedGrammar)
    }

    @Test
    fun weekDayNumParsesTokenWithoutOrdinal() {
        assertEquals(WeekDayNum(ordinal = null, dayOfWeek = DayOfWeek.MONDAY), WeekDayNum.parse("MO"))
        assertEquals(WeekDayNum(ordinal = -1, dayOfWeek = DayOfWeek.FRIDAY), WeekDayNum.parse("-1FR"))
        assertEquals(WeekDayNum(ordinal = 1, dayOfWeek = DayOfWeek.MONDAY), WeekDayNum.parse("+1MO"))
        assertNull(WeekDayNum.parse("XX"))
        assertNull(WeekDayNum.parse("0MO"))
        assertNull(WeekDayNum.parse("99MO"))
    }

    @Test
    fun preservesUntilValueType() {
        assertEquals(
            RecurrenceUntil.DateOnly(LocalDate(2026, 1, 1)),
            parseSupported("FREQ=DAILY;UNTIL=20260101").until,
        )
        assertEquals(
            RecurrenceUntil.Floating(LocalDateTime(2026, 1, 1, 12, 0, 0)),
            parseSupported("FREQ=DAILY;UNTIL=20260101T120000").until,
        )
        assertTrue(parseSupported("FREQ=DAILY;UNTIL=20260101T120000Z").until is RecurrenceUntil.DateTimeUtc)
    }

    @Test
    fun roundTripsThroughSerializer() {
        val rules = listOf(
            "FREQ=DAILY;INTERVAL=3",
            "FREQ=WEEKLY;BYDAY=MO,WE,FR;WKST=SU",
            "FREQ=MONTHLY;BYDAY=-1FR;BYSETPOS=1",
            "FREQ=YEARLY;BYWEEKNO=1,53;BYMONTH=1",
            "FREQ=DAILY;UNTIL=20260101T120000Z",
            "FREQ=DAILY;UNTIL=20260101",
            "FREQ=DAILY;UNTIL=20260101T120000",
        )
        for (rule in rules) {
            val model = parseSupported(rule)
            val reSerialized = RecurrenceRuleSerializer.serialize(model)
            assertEquals(model, parseSupported(reSerialized), "Round-trip mismatch for '$rule' → '$reSerialized'")
        }
    }

    @Test
    fun parsesCaseInsensitiveKeywordsAndValues() {
        val rule = parseSupported("freq=weekly;byday=mo,we;wkst=su")
        assertEquals(Frequency.Weekly, rule.freq)
        assertEquals(
            listOf(
                WeekDayNum(ordinal = null, dayOfWeek = DayOfWeek.MONDAY),
                WeekDayNum(ordinal = null, dayOfWeek = DayOfWeek.WEDNESDAY),
            ),
            rule.byDay,
        )
        assertEquals(DayOfWeek.SUNDAY, rule.weekStart)
    }

    @Test
    fun failsOnOutOfRangeNumericValues() {
        assertFailed("FREQ=DAILY;INTERVAL=0", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=DAILY;COUNT=0", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=DAILY;BYHOUR=24", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=DAILY;BYMINUTE=60", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=MONTHLY;BYMONTHDAY=32", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=YEARLY;BYWEEKNO=54", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=YEARLY;BYYEARDAY=367", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=MONTHLY;BYSETPOS=0", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=400", RecurrenceRuleFailureReason.MalformedGrammar)
    }

    @Test
    fun parsesNegativeBySetPosWithAnotherByRule() {
        val rule = parseSupported("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=-1")
        assertEquals(listOf(-1), rule.byOccurrencePosition)
    }

    @Test
    fun preservesDuplicateListValues() {
        assertEquals(listOf(1, 1, 2), parseSupported("FREQ=YEARLY;BYMONTH=1,1,2").byMonth)
    }

    @Test
    fun failsOnInvalidUntilFormats() {
        assertFailed("FREQ=DAILY;UNTIL=20260230", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=DAILY;UNTIL=20261301", RecurrenceRuleFailureReason.MalformedGrammar)
        assertFailed("FREQ=DAILY;UNTIL=20260101T99", RecurrenceRuleFailureReason.MalformedGrammar)
    }

    @Test
    fun serializesToCanonicalFormRegardlessOfInputOrderAndCasing() {
        val canonical = "FREQ=WEEKLY;COUNT=5;BYDAY=MO,WE"
        val reordered = "byday=mo,we;count=5;freq=weekly"
        assertEquals(canonical, RecurrenceRuleSerializer.serialize(parseSupported(canonical)))
        assertEquals(canonical, RecurrenceRuleSerializer.serialize(parseSupported(reordered)))
    }
}
