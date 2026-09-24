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
package com.infomaniak.multiplatform_calendar.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class EventDescriptionExtensionsTest {

    private val marker = "/*----------------------------------------*/"

    @Test
    fun removeMarkedBlocks_removesOneBlockAndPreservesSurroundingText() {
        val input = "before${marker}content to remove${marker}after"

        assertEquals("beforeafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesMultipleBlocks() {
        val input = "before${marker}first${marker}middle${marker}second${marker}after"

        assertEquals("beforemiddleafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesMultilineBlockAndPreservesLineBreaksOutsideIt() {
        val input = "before\n${marker}\nfirst line\nsecond line\n${marker}\nafter"

        assertEquals("before\n\nafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesSeveralMultilineBlocks() {
        val input = "before\n${marker}\nfirst block\nspanning two lines\n${marker}\nbetween\n" +
            "${marker}\nsecond block\nspanning two lines\n${marker}\nafter"

        assertEquals("before\n\nbetween\n\nafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesMultilineEmptyBlock() {
        val input = "before\n${marker}\n${marker}\nafter"

        assertEquals("before\n\nafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesBlockWithEmptyContent() {
        val input = "before${marker}${marker}after"

        assertEquals("beforeafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesSeveralEmptyBlocks() {
        val input = "before${marker}${marker}${marker}${marker}after"

        assertEquals("beforeafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesEmptyAndNonEmptyBlocksTogether() {
        val input = "before${marker}content${marker}${marker}${marker}after"

        assertEquals("beforeafter", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_returnsEmptyStringWhenOnlyAnEmptyBlockIsPresent() {
        val input = "${marker}${marker}"

        assertEquals("", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_leavesTextWithoutBlocksUntouched() {
        val input = "normal description"

        assertEquals(input, input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_leavesUnmatchedMarkerUntouched() {
        val input = "before${marker}content without closing marker"

        assertEquals(input, input.removeMarkedBlocks())
    }
}


