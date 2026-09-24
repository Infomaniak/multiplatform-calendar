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
        val input = "avant${marker}contenu à supprimer${marker}après"

        assertEquals("avantaprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesMultipleBlocks() {
        val input = "avant${marker}premier${marker}milieu${marker}second${marker}après"

        assertEquals("avantmilieuaprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesMultilineBlockAndPreservesLineBreaksOutsideIt() {
        val input = "avant\n${marker}\npremière ligne\ndeuxième ligne\n${marker}\naprès"

        assertEquals("avant\n\naprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesSeveralMultilineBlocks() {
        val input = "avant\n${marker}\npremier bloc\nsur deux lignes\n${marker}\nentre\n" +
            "${marker}\nsecond bloc\nsur deux lignes\n${marker}\naprès"

        assertEquals("avant\n\nentre\n\naprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesMultilineEmptyBlock() {
        val input = "avant\n${marker}\n${marker}\naprès"

        assertEquals("avant\n\naprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesBlockWithEmptyContent() {
        val input = "avant${marker}${marker}après"

        assertEquals("avantaprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesSeveralEmptyBlocks() {
        val input = "avant${marker}${marker}${marker}${marker}après"

        assertEquals("avantaprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_removesEmptyAndNonEmptyBlocksTogether() {
        val input = "avant${marker}contenu${marker}${marker}${marker}après"

        assertEquals("avantaprès", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_returnsEmptyStringWhenOnlyAnEmptyBlockIsPresent() {
        val input = "${marker}${marker}"

        assertEquals("", input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_leavesTextWithoutBlocksUntouched() {
        val input = "description normale"

        assertEquals(input, input.removeMarkedBlocks())
    }

    @Test
    fun removeMarkedBlocks_leavesUnmatchedMarkerUntouched() {
        val input = "avant${marker}contenu sans fermeture"

        assertEquals(input, input.removeMarkedBlocks())
    }
}


