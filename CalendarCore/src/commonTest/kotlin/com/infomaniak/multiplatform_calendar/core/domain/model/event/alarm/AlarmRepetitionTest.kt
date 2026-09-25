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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AlarmRepetitionTest {

    @Test
    fun noExtraFiring_isAllowed() {
        assertEquals(0, AlarmRepetition(count = 0, interval = 5.minutes).count)
    }

    @Test
    fun negativeCount_isRejected() {
        assertFailsWith<IllegalArgumentException> { AlarmRepetition(count = -1, interval = 5.minutes) }
    }

    @Test
    fun nonPositiveInterval_isRejected() {
        assertFailsWith<IllegalArgumentException> { AlarmRepetition(count = 2, interval = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { AlarmRepetition(count = 2, interval = (-5).minutes) }
    }
}
