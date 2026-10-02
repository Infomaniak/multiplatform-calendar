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

/**
 * Case, width and diacritic insensitive form, for search and sorting.
 *
 * Diacritics are only dropped on scripts where they are optional (Latin, Greek, Cyrillic, Arabic, Hebrew):
 * elsewhere (Thai, Indic…) combining marks are part of the spelling and are kept.
 */
internal fun String.normalizedForSearch(): String =
    caseFoldedDecomposition()
        .withoutOptionalDiacritics()
        .foldUndecomposableLetters()
        .replace(WHITESPACES, " ")
        .trim()
        .canonicalComposition()

/** Case and diacritic insensitive name comparison. */
internal val contactNameComparator: Comparator<String> = Comparator { left, right ->
    left.normalizedForSearch().compareTo(right.normalizedForSearch())
}

/** Unicode case folding with compatibility mapping (full width, ligatures…), in decomposed form (NFD). */
internal expect fun String.caseFoldedDecomposition(): String

/** Unicode canonical composition (NFC). */
internal expect fun String.canonicalComposition(): String

private val WHITESPACES = Regex("\\s+")

private val OPTIONAL_DIACRITICS_SCRIPTS = listOf(
    '\u0000'..'\u024F', // Latin, up to Extended-B
    '\u1E00'..'\u1EFF', // Latin Extended Additional
    '\u0370'..'\u03FF', // Greek
    '\u1F00'..'\u1FFF', // Greek Extended
    '\u0400'..'\u052F', // Cyrillic
    '\u0590'..'\u05FF', // Hebrew
    '\u0600'..'\u06FF', // Arabic
    '\u0750'..'\u077F', // Arabic Supplement
    '\u08A0'..'\u08FF', // Arabic Extended-A
)

/** Letters that Unicode does not decompose into a base letter and a diacritic. */
private val UNDECOMPOSABLE_LETTERS = mapOf(
    'ß' to "ss",
    'æ' to "ae",
    'œ' to "oe",
    'ø' to "o",
    'ł' to "l",
    'đ' to "d",
    'ħ' to "h",
    'ı' to "i",
)

private fun String.withoutOptionalDiacritics(): String = buildString {
    var dropMarks = false
    for (char in this@withoutOptionalDiacritics) {
        if (char.category == CharCategory.NON_SPACING_MARK) {
            if (!dropMarks) append(char)
        } else {
            dropMarks = OPTIONAL_DIACRITICS_SCRIPTS.any { char in it }
            append(char)
        }
    }
}

private fun String.foldUndecomposableLetters(): String = buildString {
    for (char in this@foldUndecomposableLetters) append(UNDECOMPOSABLE_LETTERS[char] ?: char)
}
