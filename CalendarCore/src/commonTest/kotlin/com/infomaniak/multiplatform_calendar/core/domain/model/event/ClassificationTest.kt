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
import kotlin.test.assertNull

class ClassificationTest {

    @Test
    fun fromIcalString_standardValues_areParsedCaseInsensitively() {
        assertEquals(Classification.Public, Classification.fromIcalString("PUBLIC"))
        assertEquals(Classification.Private, Classification.fromIcalString("private"))
        assertEquals(Classification.Confidential, Classification.fromIcalString("Confidential"))
    }

    @Test
    fun fromIcalString_trimsSurroundingWhitespace() {
        assertEquals(Classification.Public, Classification.fromIcalString("  PUBLIC  "))
    }

    @Test
    fun fromIcalString_nullOrBlank_isNull() {
        assertNull(Classification.fromIcalString(null))
        assertNull(Classification.fromIcalString("   "))
    }

    @Test
    fun fromIcalString_unknownToken_becomesCustomVerbatim() {
        assertEquals(Classification.Custom("X-PERSONAL"), Classification.fromIcalString("X-PERSONAL"))
    }

    @Test
    fun toIcalString_returnsCanonicalTokens() {
        assertEquals("PUBLIC", Classification.Public.toIcalString())
        assertEquals("PRIVATE", Classification.Private.toIcalString())
        assertEquals("CONFIDENTIAL", Classification.Confidential.toIcalString())
        assertEquals("X-PERSONAL", Classification.Custom("X-PERSONAL").toIcalString())
    }

    @Test
    fun roundTrip_standardAndCustom_arePreserved() {
        listOf(
            Classification.Public,
            Classification.Private,
            Classification.Confidential,
            Classification.Custom("X-PERSONAL"),
        ).forEach { classification ->
            assertEquals(classification, Classification.fromIcalString(classification.toIcalString()))
        }
    }
}
