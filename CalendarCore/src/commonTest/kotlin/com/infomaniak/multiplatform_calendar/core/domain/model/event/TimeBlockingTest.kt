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

class TimeBlockingTest {

    @Test
    fun standardTokens_areParsed() {
        assertEquals(TimeBlocking.Blocks, TimeBlocking.fromIcalString("OPAQUE"))
        assertEquals(TimeBlocking.DoesNotBlock, TimeBlocking.fromIcalString("TRANSPARENT"))
    }

    @Test
    fun tokens_areParsedCaseInsensitivelyAndTrimmed() {
        assertEquals(TimeBlocking.Blocks, TimeBlocking.fromIcalString(" opaque "))
        assertEquals(TimeBlocking.DoesNotBlock, TimeBlocking.fromIcalString("Transparent"))
    }

    @Test
    fun absentOrBlankValue_isNull() {
        assertNull(TimeBlocking.fromIcalString(null))
        assertNull(TimeBlocking.fromIcalString("   "))
    }

    /** The RFC 5545 §3.8.2.7 grammar is closed, so anything else is dropped rather than kept verbatim. */
    @Test
    fun valueOutsideTheRfcSet_isNull() {
        assertNull(TimeBlocking.fromIcalString("X-TENTATIVE"))
    }

    /** The enum constants are named for the domain, so the iCal tokens must not follow them. */
    @Test
    fun icalTokens_areEmittedVerbatim() {
        assertEquals("OPAQUE", TimeBlocking.Blocks.toIcalString())
        assertEquals("TRANSPARENT", TimeBlocking.DoesNotBlock.toIcalString())
    }

    @Test
    fun everyValue_roundTripsThroughItsIcalToken() {
        TimeBlocking.entries.forEach {
            assertEquals(it, TimeBlocking.fromIcalString(it.toIcalString()))
        }
    }
}
