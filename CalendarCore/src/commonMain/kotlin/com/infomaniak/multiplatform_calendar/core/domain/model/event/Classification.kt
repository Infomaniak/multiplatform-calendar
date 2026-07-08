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

/**
 * Access classification of an event (RFC 5545 §3.8.1.3, the `CLASS` property).
 *
 * The three standard values are modelled as objects; any non-standard `x-name` / `iana-token`
 * (which the RFC explicitly allows) is preserved verbatim as [Custom] so it round-trips back to the
 * CalDAV server untouched. A `null` classification means the property was absent (distinct from an
 * explicit `PUBLIC`).
 */
public sealed interface Classification {

    public data object Public : Classification

    public data object Private : Classification

    public data object Confidential : Classification

    /** A non-standard token (`x-name` / `iana-token`), kept as-is for lossless round-trip. */
    public data class Custom(val token: String) : Classification

    /** The canonical iCalendar token for this classification. */
    public fun toIcalString(): String = when (this) {
        Public -> PUBLIC
        Private -> PRIVATE
        Confidential -> CONFIDENTIAL
        is Custom -> token
    }

    public companion object {
        private const val PUBLIC = "PUBLIC"
        private const val PRIVATE = "PRIVATE"
        private const val CONFIDENTIAL = "CONFIDENTIAL"

        /**
         * Parse a raw iCalendar `CLASS` value into a [Classification], or `null` when absent/blank.
         * Standard tokens are matched case-insensitively; anything else becomes [Custom] verbatim.
         */
        public fun fromIcalString(raw: String?): Classification? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return when (value.uppercase()) {
                PUBLIC -> Public
                PRIVATE -> Private
                CONFIDENTIAL -> Confidential
                else -> Custom(value)
            }
        }
    }
}
