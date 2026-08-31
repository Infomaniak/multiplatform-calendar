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
 * Whether an event blocks the time slot it occupies, per RFC 5545 §3.8.2.7 (the `TRANSP` property).
 *
 * Only free/busy lookups are affected (CalDAV `free-busy-query`, RFC 4791 §7.10): a [DoesNotBlock]
 * event stays visible in the calendar but is excluded from the computed `VFREEBUSY`. Unrelated to
 * [EventStatus], which says where the event stands rather than whether it blocks time.
 *
 * The RFC grammar is closed (`"OPAQUE" / "TRANSPARENT"`), so unknown values are dropped like in
 * [EventStatus]. A `null` means the property was absent, which the RFC defines as [Blocks].
 */
public enum class TimeBlocking(private val icalValue: String) {
    /** `OPAQUE` — the slot counts as busy. */
    Blocks("OPAQUE"),

    /** `TRANSPARENT` — the slot stays free. */
    DoesNotBlock("TRANSPARENT");

    /** The canonical iCalendar `TRANSP` token for this value. */
    public fun toIcalString(): String = icalValue

    public companion object {
        /**
         * Parse a raw iCalendar `TRANSP` value, or `null` when absent, blank or outside the RFC set.
         * Comparisons are ASCII case-insensitive per RFC 5545 §3.7.3.
         */
        public fun fromIcalString(value: String?): TimeBlocking? {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.icalValue.equals(normalized, ignoreCase = true) }
        }
    }
}
