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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.MeetRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun withMarkedBlocksFrom_movesBlocksToTheEndAndDiscardsOldInterleavedText() {
        val original = "before${marker}block 1${marker}between${marker}block 2${marker}after"
        val expected = "updated\n\n${marker}block 1${marker}\n\n${marker}block 2${marker}"

        assertEquals(expected, "updated".withMarkedBlocksFrom(original))
    }

    @Test
    fun withMarkedBlocksFrom_keepsBlocksWhenEditedTextIsEmpty() {
        val original = "before${marker}block${marker}after"
        assertEquals("${marker}block${marker}", "".withMarkedBlocksFrom(original))
    }

    @Test
    fun withMarkedBlocksFrom_hasNoEmptyResultIfThereAreNoBlocks() {
        assertEquals(null, "  ".withMarkedBlocksFrom("old text"))
        assertEquals("new", " new ".withMarkedBlocksFrom(null))
    }

    @Test
    fun withMarkedBlocksFrom_doesNotDuplicateBlocksSuppliedInEditedText() {
        val original = "${marker}block${marker}"
        assertEquals("new\n\n$original", "new$original".withMarkedBlocksFrom(original))
    }

    @Test
    fun withMarkedBlocksFrom_preservesOriginalWhenTextIsUnchanged() {
        val original = "before\n${marker}metadata${marker}\nafter"
        assertEquals(original, "before\n\nafter".withMarkedBlocksFrom(original))
    }

    @Test
    fun withMarkedBlocksFrom_preservesOriginalFormattingWhenUnchanged() {
        val original = "  description  \n\n${marker}metadata${marker}\n"
        assertEquals(original, "description".withMarkedBlocksFrom(original))
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

    @Test
    fun withMeetRoomBlock_leavesTheDescriptionAloneWhenTheRoomIsUnchanged() {
        val description = "Agenda\n\n${meetBlock(URL)}"

        assertEquals(description, description.withMeetRoomBlock(URL, MeetRoom(URL)))
    }

    @Test
    fun withMeetRoomBlock_appendsTheBlockOfANewRoom() {
        assertEquals("Agenda\n\n${meetBlock(URL)}", "Agenda".withMeetRoomBlock(null, meetRoom(URL)))
    }

    @Test
    fun withMeetRoomBlock_isTheBlockAloneOnAnEmptyDescription() {
        assertEquals(meetBlock(URL), null.withMeetRoomBlock(null, meetRoom(URL)))
    }

    @Test
    fun withMeetRoomBlock_replacesTheBlockOfThePreviousRoomAndKeepsTheOtherBlocks() {
        val other = "${marker}\nother\n${marker}"
        val description = "Agenda\n\n${meetBlock(URL)}\n\n$other"

        assertEquals(
            "Agenda\n\n$other\n\n${meetBlock(OTHER_URL)}",
            description.withMeetRoomBlock(URL, meetRoom(OTHER_URL)),
        )
    }

    @Test
    fun withMeetRoomBlock_removesTheBlockWithTheRoom() {
        assertEquals("Agenda", "Agenda\n\n${meetBlock(URL)}".withMeetRoomBlock(URL, null))
    }

    @Test
    fun withMeetRoomBlock_isNullOnceTheOnlyBlockIsRemoved() {
        assertNull(meetBlock(URL).withMeetRoomBlock(URL, null))
    }

    @Test
    fun withMeetRoomBlock_ignoresABlankPreviousUrl() {
        val description = "Agenda\n\n${marker}\nother\n${marker}"

        assertEquals("$description\n\n${meetBlock(URL)}", description.withMeetRoomBlock("", meetRoom(URL)))
    }

    private fun meetBlock(url: String) = markedBlock("Join $url")

    private fun meetRoom(url: String) = MeetRoom(url, descriptionBlock = meetBlock(url))

    private companion object {
        const val URL = "https://kmeet.infomaniak.com/abc"
        const val OTHER_URL = "https://kmeet.infomaniak.com/xyz"
    }
}
