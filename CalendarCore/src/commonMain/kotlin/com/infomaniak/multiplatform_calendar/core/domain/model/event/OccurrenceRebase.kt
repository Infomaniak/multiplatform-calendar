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

import com.infomaniak.multiplatform_calendar.core.extensions.shiftedBy
import com.infomaniak.multiplatform_calendar.core.extensions.wallClockShift
import kotlinx.datetime.LocalDateTime
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.MasterTiming
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC

/**
 * This timing, edited on one occurrence, expressed on the [master] it is about to be written onto.
 *
 * An expanded occurrence shows the master's fields on its own slot, so edit data prepared from it
 * carries that slot rather than `DTSTART`. Writing it verbatim would drag the whole series onto the
 * edited occurrence; what the user moved is the *distance* from [shownStart], and that is what the
 * master is shifted by — leaving it untouched when the time was not part of the edit.
 *
 * The arithmetic is done on calendar faces (in [UTC], a zone with no transition to trip over): the
 * series keeps the local time it is read at, whatever offsets lie between the two dates. The end is
 * then derived the way the expansion derives an occurrence's, so that an edit read back and handed
 * over untouched lands on the master's own end. Everything else — zones, all-day, the rule — is the
 * edit's own and stays as given.
 */
internal fun EventTiming.rebasedOnto(master: EventTiming, shownStart: LocalDateTime, defaultZone: TimeZone): EventTiming {
    val shift = wallClockShift(from = shownStart, to = start)
    val rebasedStart = master.start.shiftedBy(shift)
    val edited = MasterTiming.of(master = this, defaultZone = defaultZone)
    val (rebasedEnd, _) = edited.occurrenceEnd(rebasedStart, edited.resolvedStartInstant(rebasedStart))

    return copy(start = rebasedStart, end = rebasedEnd)
}
