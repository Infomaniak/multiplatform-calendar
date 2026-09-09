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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue

/**
 * What a save should do to a recurrence master's excluded (`EXDATE`) or extra (`RDATE`) dates.
 *
 * Internal on purpose: an edit form never sets these. They hold the accumulated history of a series'
 * exceptions, and only occurrence-level operations ("this event only" / "this and following") write
 * them, via [Set] with the previous list plus or minus one date. [Preserve] is the default everywhere
 * else, so a plain event edit can't drop them by omission.
 */
internal sealed interface DateListEdit {

    /** Keep whatever the event already carries. */
    data object Preserve : DateListEdit

    /** Replace the whole set with [values]; empty behaves as [Clear]. */
    data class Set(val values: List<IcalDateValue>) : DateListEdit

    /** Drop every value of that property. */
    data object Clear : DateListEdit
}
