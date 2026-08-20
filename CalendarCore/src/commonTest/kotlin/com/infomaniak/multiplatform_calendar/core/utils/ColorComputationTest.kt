package com.infomaniak.multiplatform_calendar.core.utils

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

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
class ColorComputationTest {

    @Test
    fun eventColors_sameSourceColor_reusesCachedComputation() {
        val color = 0xFF1E88E5.toInt()

        val first = EventColors.from(eventSourceColor = color, calendarSourceColor = color)
        val sizeAfterFirst = ColorComputation.cache.size
        val second = EventColors.from(eventSourceColor = color, calendarSourceColor = color)
        val sizeAfterSecond = ColorComputation.cache.size

        assertSame(first.onContainerColor, second.onContainerColor)
        assertSame(first.onContainerVariantColor, second.onContainerVariantColor)
        assertEquals(sizeAfterFirst, sizeAfterSecond)

    }

    @Test
    fun eventColors_distinctSourceColors_createNewColorComputationInCache() {
        val calendarColor = 0xFFE53935.toInt()
        val event1Color = 0xFF1E88E5.toInt()
        val event2Color = 0xFFE53935.toInt()

        val first = EventColors.from(eventSourceColor = event1Color, calendarSourceColor = calendarColor)
        val sizeAfterFirst = ColorComputation.cache.size
        val second = EventColors.from(eventSourceColor = event2Color, calendarSourceColor = calendarColor)
        val sizeAfterSecond = ColorComputation.cache.size

        assertNotSame(first.onContainerColor, second.onContainerColor)
        assertNotSame(first.onContainerVariantColor, second.onContainerVariantColor)
        assertNotEquals(sizeAfterFirst, sizeAfterSecond)
    }

}
