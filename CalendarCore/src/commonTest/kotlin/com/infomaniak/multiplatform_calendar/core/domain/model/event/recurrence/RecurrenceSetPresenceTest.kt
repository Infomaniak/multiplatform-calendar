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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// EXDATE is not a parameter on purpose: it subtracts from a set it cannot create.
class RecurrenceSetPresenceTest {

    @Test
    fun aRuleMakesASeries() {
        assertTrue(hasRecurrenceSet(RecurrenceRule(freq = Frequency.Daily), rDates = emptyList()))
    }

    @Test
    fun listedDatesAloneMakeASeries() {
        assertTrue(hasRecurrenceSet(recurrenceRule = null, rDates = listOf(rDate)))
    }

    @Test
    fun aRuleAndListedDatesMakeASeries() {
        assertTrue(hasRecurrenceSet(RecurrenceRule(freq = Frequency.Daily), rDates = listOf(rDate)))
    }

    @Test
    fun neitherMakesAPlainEvent() {
        assertFalse(hasRecurrenceSet(recurrenceRule = null, rDates = emptyList()))
    }

    private val rDate = IcalDateValue.Zoned(
        LocalDateTime(2026, 6, 20, 10, 0).toInstant(TimeZone.UTC),
        TimeZone.UTC.id,
    )
}
