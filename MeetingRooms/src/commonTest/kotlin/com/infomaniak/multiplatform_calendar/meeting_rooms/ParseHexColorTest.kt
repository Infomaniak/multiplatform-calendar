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
package com.infomaniak.multiplatform_calendar.meeting_rooms

import com.infomaniak.multiplatform_calendar.meeting_rooms.data.remote.model.parseHexColor
import com.infomaniak.multiplatform_calendar.meeting_rooms.domain.model.MeetingRoomColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseHexColorTest {

    private val red = MeetingRoomColor(0xFFFF0000.toInt())

    @Test
    fun sixDigitsAreOpaque() = assertEquals(red, parseHexColor("#FF0000"))

    @Test
    fun threeDigitsAreExpanded() = assertEquals(red, parseHexColor("#f00"))

    @Test
    fun eightDigitsEndWithTheAlpha() = assertEquals(MeetingRoomColor(0x80FF0000.toInt()), parseHexColor("#FF000080"))

    @Test
    fun hashIsOptional() = assertEquals(red, parseHexColor("FF0000"))

    @Test
    fun malformedColorIsNull() = assertNull(parseHexColor("red"))

    @Test
    fun missingColorIsNull() = assertNull(parseHexColor(null))
}
