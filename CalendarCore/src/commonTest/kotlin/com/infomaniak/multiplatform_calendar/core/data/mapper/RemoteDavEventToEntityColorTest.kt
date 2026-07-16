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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseCss3ColorName
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteDavEventToEntityColorTest {

    private val calendarId = CalendarId("calendar://tests")

    // ---- resolveColorArgb: hex wins > CSS3 name > null ------------------------------------------

    @Test
    fun noColorProperties_resolveToNull() {
        val entity = remoteEvent(colorHex = null, colorIcalName = null).toEntity(calendarId)

        assertNull(entity.colorArgb)
        assertNull(entity.colorIcalName)
    }

    @Test
    fun onlyAppleHex_isStoredAsArgb_andIcalNameStaysNull() {
        val entity = remoteEvent(colorHex = "#1E88E5FF", colorIcalName = null).toEntity(calendarId)

        assertEquals(0xFF1E88E5.toInt(), entity.colorArgb)
        assertNull(entity.colorIcalName)
    }

    @Test
    fun onlyRfc7986CssName_isResolvedToArgb_andIcalNameIsKeptForRoundTrip() {
        val entity = remoteEvent(colorHex = null, colorIcalName = "royalblue").toEntity(calendarId)

        assertEquals(0xFF4169E1.toInt(), entity.colorArgb)
        assertEquals("royalblue", entity.colorIcalName)
    }

    @Test
    fun bothPropertiesPresent_hexWinsOverCssName() {
        // Apple Calendar writes both with disagreeing values; the hex is authoritative.
        val entity = remoteEvent(colorHex = "#E53935FF", colorIcalName = "royalblue").toEntity(calendarId)

        assertEquals(0xFFE53935.toInt(), entity.colorArgb)
        assertEquals("royalblue", entity.colorIcalName)
    }

    @Test
    fun malformedHexFallsBackToCssName() {
        val entity = remoteEvent(colorHex = "not-a-hex", colorIcalName = "royalblue").toEntity(calendarId)

        assertEquals(0xFF4169E1.toInt(), entity.colorArgb)
    }

    @Test
    fun unknownCssName_withNoHex_resolvesToNull() {
        val entity = remoteEvent(colorHex = null, colorIcalName = "not-a-css3-color").toEntity(calendarId)

        assertNull(entity.colorArgb)
        assertEquals("not-a-css3-color", entity.colorIcalName)
    }

    // ---- parseCss3ColorName --------------------------------------------------------------------

    @Test
    fun parseCss3ColorName_isCaseInsensitive() {
        assertEquals(parseCss3ColorName("royalblue"), parseCss3ColorName("RoyalBlue"))
        assertEquals(parseCss3ColorName("royalblue"), parseCss3ColorName("ROYALBLUE"))
    }

    @Test
    fun parseCss3ColorName_returnsNullForBlankOrUnknown() {
        assertNull(parseCss3ColorName(null))
        assertNull(parseCss3ColorName(""))
        assertNull(parseCss3ColorName("   "))
        assertNull(parseCss3ColorName("not-a-css3-color"))
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun remoteEvent(colorHex: String?, colorIcalName: String?) = RemoteDavEvent(
        url = "https://cal/tests/color.ics",
        etag = "etag-1",
        icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
        uid = "uid-1",
        summary = "Test",
        description = null,
        location = null,
        dtstart = "20260615T100000Z",
        dtStartTzid = null,
        dtend = "20260615T110000Z",
        dtEndTzid = null,
        duration = null,
        created = null,
        lastModified = null,
        dtstamp = null,
        rrule = null,
        status = null,
        transp = null,
        classification = null,
        priority = null,
        sequence = null,
        categories = null,
        colorHex = colorHex,
        colorIcalName = colorIcalName,
        attendees = emptyList(),
    )
}
