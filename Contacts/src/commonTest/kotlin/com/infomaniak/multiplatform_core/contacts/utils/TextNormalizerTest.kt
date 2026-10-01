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
package com.infomaniak.multiplatform_core.contacts.utils

import com.infomaniak.multiplatform_core.contacts.RobolectricTestsBase
import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest : RobolectricTestsBase() {

    @Test
    fun foldsCaseAndCollapsesWhitespaces() {
        assertNormalized("jean dupont", "  JEAN \t Dupont ")
    }

    @Test
    fun removesLatinDiacritics() {
        assertNormalized("eleonore francois", "Éléonore François")
        assertNormalized("nguyen van duc", "Nguyễn Văn Đức")
    }

    @Test
    fun foldsLettersWithoutDecomposition() {
        assertNormalized("strasse", "Straße")
        assertNormalized("lukasz zolc", "Łukasz Żółć")
        assertNormalized("soren aero", "Søren Ærø")
        assertNormalized("oeuvre", "Œuvre")
    }

    @Test
    fun foldsTurkishDottedAndDotlessI() {
        assertNormalized("istanbul", "İstanbul")
        assertNormalized("ilik", "ılık")
    }

    @Test
    fun removesGreekAndCyrillicDiacritics() {
        assertNormalized("σισυφοσ", "Σίσυφος")
        assertNormalized("елка", "Ёлка")
    }

    @Test
    fun removesArabicAndHebrewVowelMarks() {
        assertNormalized("محمد", "مُحَمَّد")
        assertNormalized("احمد", "أحمد")
        assertNormalized("שלום", "שָׁלוֹם")
    }

    @Test
    fun keepsMarksThatAreSpelling() {
        assertNormalized("สมศักดิ์", "สมศักดิ์")
        assertNormalized("प्रिया हिंदी", "प्रिया हिंदी")
        assertNormalized("がっこう", "がっこう")
    }

    @Test
    fun foldsWidthVariants() {
        assertNormalized("john", "ＪＯＨＮ")
        assertNormalized("ガ", "ｶﾞ")
    }

    @Test
    fun leavesIdeographsUnchanged() {
        assertNormalized("王小明", "王小明")
    }

    private fun assertNormalized(expected: String, input: String) = assertEquals(expected, input.normalizedForSearch())
}
