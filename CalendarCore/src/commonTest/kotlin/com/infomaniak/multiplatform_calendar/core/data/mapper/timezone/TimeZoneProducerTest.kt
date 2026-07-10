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
package com.infomaniak.multiplatform_calendar.core.data.mapper.timezone

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeZoneProducerTest {

    @Test
    fun allDay_returnsNull() {
        assertNull(resolve(isAllDay = true, rawValue = "20260615", tzid = null))
        // Even with a TZID, all-day trumps everything.
        assertNull(resolve(isAllDay = true, rawValue = "20260615", tzid = "Europe/Paris"))
    }

    @Test
    fun utcSuffix_returnsUtc() {
        assertEquals(TimeZone.UTC, resolve(isAllDay = false, rawValue = "20260615T100000Z", tzid = null))
        // Z suffix trumps a TZID (defensive: no TZID should coexist with Z per RFC 5545, but if it does, Z wins).
        assertEquals(TimeZone.UTC, resolve(isAllDay = false, rawValue = "20260615T100000Z", tzid = "Europe/Paris"))
    }

    @Test
    fun validIanaTzid_resolves() {
        assertEquals(TimeZone.of("Europe/Paris"), resolve(isAllDay = false, rawValue = "20260615T100000", tzid = "Europe/Paris"))
    }

    @Test
    fun mozillaGloballyUniqueTzid_isStrippedAndResolved() {
        // RFC 5545 §3.2.19 "globally unique" form used by Thunderbird / Mozilla.
        assertEquals(
            TimeZone.of("Europe/Paris"),
            resolve(isAllDay = false, rawValue = "20260615T100000", tzid = "/mozilla.org/20050126_1/Europe/Paris"),
        )
    }

    @Test
    fun freeassociationGloballyUniqueTzid_isStrippedAndResolved() {
        // Same RFC 5545 §3.2.19 "globally unique" form, emitted by GNOME Evolution.
        assertEquals(
            TimeZone.of("Europe/Paris"),
            resolve(
                isAllDay = false,
                rawValue = "20260615T100000",
                tzid = "/freeassociation.sourceforge.net/Tzfile/Europe/Paris",
            ),
        )
    }

    @Test
    fun msTzid_isMappedToIana() {
        assertEquals(
            TimeZone.of("Europe/Paris"),
            resolve(isAllDay = false, rawValue = "20260615T100000", tzid = "Romance Standard Time"),
        )
    }

    @Test
    fun unknownTzid_fallsBackToUtc() {
        // Neither valid IANA, nor globally-unique prefix, nor MS mapping → UTC fallback (logged to Sentry).
        assertEquals(TimeZone.UTC, resolve(isAllDay = false, rawValue = "20260615T100000", tzid = "Middle/Earth"))
    }

    @Test
    fun floating_noTzidNoUtcSuffix_returnsNull() {
        assertNull(resolve(isAllDay = false, rawValue = "20260615T100000", tzid = null))
    }

    private fun resolve(isAllDay: Boolean, rawValue: String, tzid: String?): TimeZone? =
        resolveTimeZone(isAllDay, rawValue, tzid, eventUrl = "event://test", propertyName = "DTSTART")
}
