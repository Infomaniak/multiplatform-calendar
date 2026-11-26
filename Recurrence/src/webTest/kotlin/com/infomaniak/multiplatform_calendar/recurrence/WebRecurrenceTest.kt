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
package com.infomaniak.multiplatform_calendar.recurrence

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebRecurrenceTest {

    @Test
    fun unsupportedRuleIsReportedToFailureCallback() = runTest {
        val failure = CompletableDeferred<String>()
        formatRRule(
            rawRRule = "FREQ=INVALID",
            start = "2026-01-01T10:00:00",
            timeZoneId = null,
            onSuccess = { failure.complete("Unexpected success: $it") },
            onFailure = { failure.complete(it) },
        )

        assertTrue(failure.await().startsWith("Unsupported RRULE:"))
    }

    @Test
    fun unsupportedRuleFailsWithReason() = runTest {
        assertFailsWith<IllegalArgumentException> {
            formatRRuleText(
                rawRRule = "FREQ=INVALID",
                start = "2026-01-01T10:00:00",
                timeZoneId = null,
            )
        }
    }

    @Test
    fun invalidMasterStartFails() = runTest {
        assertFailsWith<IllegalArgumentException> {
            formatRRuleText(
                rawRRule = "FREQ=DAILY",
                start = "not-a-date",
                timeZoneId = null,
            )
        }
    }

    @Test
    fun invalidTimeZoneFails() = runTest {
        assertFailsWith<IllegalArgumentException> {
            formatRRuleText(
                rawRRule = "FREQ=DAILY",
                start = "2026-01-01T10:00:00",
                timeZoneId = "Not/A-Time-Zone",
            )
        }
    }
}
