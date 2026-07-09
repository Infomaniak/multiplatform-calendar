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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaySpanPositionTest {

    @Test
    fun singleDay_isFirstAndLast() {
        val position = DaySpanPosition(index = 0, count = 1)
        assertTrue(position.isFirst)
        assertTrue(position.isLast)
        assertTrue(position.isSingleDay)
        assertEquals(1, position.number)
        assertEquals("1/1", position.toString())
    }

    @Test
    fun middleDay_isNeitherFirstNorLast() {
        val position = DaySpanPosition(index = 1, count = 3)
        assertFalse(position.isFirst)
        assertFalse(position.isLast)
        assertFalse(position.isSingleDay)
        assertEquals(2, position.number)
        assertEquals("2/3", position.toString())
    }

    @Test
    fun lastDay_isLastOnly() {
        val position = DaySpanPosition(index = 22, count = 23)
        assertFalse(position.isFirst)
        assertTrue(position.isLast)
        assertEquals("23/23", position.toString())
    }

    @Test
    fun rejectsNonPositiveCount() {
        assertFailsWith<IllegalArgumentException> { DaySpanPosition(index = 0, count = 0) }
    }

    @Test
    fun rejectsIndexOutOfBounds() {
        assertFailsWith<IllegalArgumentException> { DaySpanPosition(index = 3, count = 3) }
        assertFailsWith<IllegalArgumentException> { DaySpanPosition(index = -1, count = 3) }
    }
}
