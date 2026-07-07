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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventStatusTest {

    @Test
    fun fromIcalString_parsesRfcValuesCaseInsensitively() {
        assertEquals(EventStatus.TENTATIVE, EventStatus.fromIcalString("TENTATIVE"))
        assertEquals(EventStatus.CONFIRMED, EventStatus.fromIcalString("Confirmed"))
        assertEquals(EventStatus.CANCELLED, EventStatus.fromIcalString("cancelled"))
    }

    @Test
    fun fromIcalString_trimsSurroundingWhitespace() {
        assertEquals(EventStatus.CONFIRMED, EventStatus.fromIcalString("  CONFIRMED  "))
    }

    @Test
    fun fromIcalString_returnsNullForNullBlankAndUnknownValues() {
        assertNull(EventStatus.fromIcalString(null))
        assertNull(EventStatus.fromIcalString(""))
        assertNull(EventStatus.fromIcalString("   "))
        // VTODO / VJOURNAL statuses must not leak into VEVENT.
        assertNull(EventStatus.fromIcalString("NEEDS-ACTION"))
        assertNull(EventStatus.fromIcalString("COMPLETED"))
        assertNull(EventStatus.fromIcalString("DRAFT"))
        assertNull(EventStatus.fromIcalString("FINAL"))
        assertNull(EventStatus.fromIcalString("X-CUSTOM"))
    }
}
