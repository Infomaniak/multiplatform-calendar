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
package com.infomaniak.multiplatform_calendar.core.domain.recurrence

/** Why [RecurrenceExpander.expandInto] stopped, so callers can log truncation towards Sentry. */
internal enum class ExpansionOutcome {
    /** The series terminated naturally (COUNT / UNTIL / no more instances inside the window). */
    Completed,

    /** Truncated: [ExpansionLimits.maxGeneratedOccurrences] reached. */
    TruncatedByOccurrenceCap,

    /** Stopped: [ExpansionLimits.maxScannedPeriods] consecutive empty periods scanned. */
    StoppedByConsecutiveEmptyPeriods,
}
