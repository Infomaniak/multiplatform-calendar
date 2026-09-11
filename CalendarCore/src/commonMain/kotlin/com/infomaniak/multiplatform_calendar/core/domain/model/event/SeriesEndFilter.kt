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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey.Utc
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.isExceededBy
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.MasterTiming
import kotlinx.datetime.TimeZone

/**
 * Rejects the `RECURRENCE-ID`s a series no longer holds, as left behind by a shortening that did not clean up
 * its out-of-range overrides. Shared by every view, so all of them drop the same overrides.
 *
 * Tested on the slot, never on the override's own `DTSTART`: an instance legitimately moved past `UNTIL` keeps
 * an in-range slot. An `RDATE` slot is never an orphan, `UNTIL` not bounding it (RFC 5545 §3.8.5.2).
 *
 * Only `UNTIL` is checked: a `COUNT` series has no bound here without replaying the rule, and a shortening
 * rewrites `UNTIL` anyway.
 */
internal class SeriesEndFilter private constructor(
    private val master: EventTiming,
    private val until: RecurrenceUntil,
    private val masterTiming: MasterTiming,
    private val rDateKeys: Set<RecurrenceKey>,
    private val timeZone: TimeZone,
) {
    fun isOrphan(slot: RecurrenceKey): Boolean {
        if (slot in rDateKeys) return false

        // A slot whose value type contradicts the master's is unreadable here: keep it, erring on the safe side.
        val localStart = slot.toLocalStart(master, timeZone) ?: return false
        val instantStart = if (slot is Utc) slot.instant else masterTiming.resolvedStartInstant(localStart)

        return until.isExceededBy(localStart, instantStart)
    }

    companion object {
        /** `null` when the series has no `UNTIL`, so no slot of it can be out of range. */
        fun of(master: EventTiming, timeZone: TimeZone): SeriesEndFilter? {
            val until = master.recurrenceRule?.until ?: return null
            return SeriesEndFilter(
                master = master,
                until = until,
                masterTiming = MasterTiming.of(master, timeZone),
                rDateKeys = master.rDates.mapNotNullTo(HashSet()) { it.toRecurrenceKey(master) },
                timeZone = timeZone,
            )
        }
    }
}
