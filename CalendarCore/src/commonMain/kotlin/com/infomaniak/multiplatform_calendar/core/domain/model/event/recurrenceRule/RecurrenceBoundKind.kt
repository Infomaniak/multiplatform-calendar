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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule

/**
 * The nature of a recurring series' upper bound, stored alongside the bound instant so the range
 * query can tell three cases apart without conflating them under a single `NULL` (RFC 5545 §3.3.10):
 *
 * - [Finite]: the series has a computable last occurrence — its end instant is stored in
 *   `EventEntity.lastPossibleOccurrenceEndInstantMs` (or the floating/all-day local pendant). A range
 *   starting after that instant can safely skip the series.
 * - [Infinite]: the series never ends (`RRULE` without `UNTIL`/`COUNT`); the bound instant is `NULL`
 *   and the series always passes the upper-bound test.
 * - [FiniteDeferred]: the series is finite (`COUNT` without `UNTIL`) with a perfectly computable last
 *   occurrence, but computing it is **deliberately deferred as a sync-time optimization**: expanding a
 *   potentially high `COUNT` for every event at sync would be wasteful. The bound instant is therefore
 *   `NULL` and the series is treated as open-ended by the query, the real cut-off being applied by the
 *   expander at read time. Not to be confused with an *unknown* end — the end is known, just not
 *   pre-computed.
 */
internal enum class RecurrenceBoundKind {
    Finite,
    Infinite,
    FiniteDeferred,
}
