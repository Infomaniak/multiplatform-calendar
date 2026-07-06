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

/**
 * Overall status/confirmation for a `VEVENT`, per RFC 5545 §3.8.1.11.
 *
 * The `STATUS` property is optional (`0..1`) and, restricted to event components, only accepts
 * `"TENTATIVE" / "CONFIRMED" / "CANCELLED"`. Unknown values are dropped rather than surfaced as a
 * separate variant because (a) the property is optional so `null` is a valid encoding and (b) it
 * keeps the domain model closed over the RFC-defined set.
 *
 * VTODO/VJOURNAL statuses are intentionally excluded — this app only parses VEVENT.
 */
public enum class EventStatus {
    TENTATIVE,
    CONFIRMED,
    CANCELLED;

    public companion object {
        /**
         * Parse a raw iCalendar `STATUS` value into an [EventStatus].
         *
         * Comparisons are ASCII case-insensitive per RFC 5545 §3.7.3. Blank input and any value
         * outside the VEVENT-legal set return `null`.
         */
        public fun fromIcalString(value: String?): EventStatus? {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}
