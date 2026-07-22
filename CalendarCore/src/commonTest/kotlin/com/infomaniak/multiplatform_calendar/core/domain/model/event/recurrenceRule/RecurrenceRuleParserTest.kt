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
    fun ignoresExtensionTokens() {
        assertEquals(Frequency.Daily, parseSupported("FREQ=DAILY;X-CUSTOM=whatever").freq)
    }

    @Test
    fun takesFirstRuleLine() {
        assertEquals(Frequency.Daily, parseSupported("FREQ=DAILY\nFREQ=WEEKLY").freq)
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
        assertNull(WeekDayNum.parse("XX"))
    }

    @Test
    fun roundTripsThroughSerializer() {
        val rules = listOf(
            "FREQ=DAILY;INTERVAL=3",
            "FREQ=WEEKLY;BYDAY=MO,WE,FR;WKST=SU",
            "FREQ=MONTHLY;BYDAY=-1FR;BYSETPOS=1",
            "FREQ=YEARLY;BYWEEKNO=1,53;BYMONTH=1",
            "FREQ=DAILY;UNTIL=20260101T120000Z",
        )
        for (rule in rules) {
            val model = parseSupported(rule)
            val reSerialized = RecurrenceRuleSerializer.serialize(model)
            assertEquals(model, parseSupported(reSerialized), "Round-trip mismatch for '$rule' → '$reSerialized'")
        }
    }
}
