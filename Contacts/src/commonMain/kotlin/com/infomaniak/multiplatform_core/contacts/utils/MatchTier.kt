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

/** How well a search query matches a contact, from best to worst. */
internal enum class MatchTier {
    StartOfText,
    StartOfWord,
    Inside,
    None;

    companion object {
        /** Best tier of [normalizedQuery] among [normalizedFields], all normalized with [normalizedForSearch]. */
        fun of(normalizedQuery: String, vararg normalizedFields: String): MatchTier =
            normalizedFields.minOfOrNull { it.matchTier(normalizedQuery) } ?: None

        private fun String.matchTier(query: String): MatchTier {
            var index = indexOf(query)
            if (index < 0) return None
            if (index == 0) return StartOfText
            while (index >= 0) {
                if (!this[index - 1].continuesWord()) return StartOfWord
                index = indexOf(query, startIndex = index + 1)
            }
            return Inside
        }
    }
}

/** Combining marks, such as Devanagari vowel signs, belong to the word of the letter they modify. */
private fun Char.continuesWord(): Boolean = isLetterOrDigit() || category in COMBINING_MARKS

private val COMBINING_MARKS = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK)
