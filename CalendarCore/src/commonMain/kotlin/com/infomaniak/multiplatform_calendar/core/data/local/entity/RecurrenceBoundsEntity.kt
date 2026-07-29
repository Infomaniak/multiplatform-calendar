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
package com.infomaniak.multiplatform_calendar.core.data.local.entity

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind
import kotlinx.datetime.LocalDateTime

/**
 * Bounds of a recurring master's recurrence *set* (RRULE only for now — RDATE/EXDATE/overrides come in
 * PR 10/11), grouped as a nullable Room `@Embedded` on [EventEntity] and computed at sync so
 * [EventDao.observeVisibleInRange][com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao.observeVisibleInRange]
 * can keep or skip a master without expanding it.
 *
 * With no column prefix the fields keep their own column names, so this is a pure code-level grouping:
 * same columns, same indices, no SQL migration, no DAO change. The whole `@Embedded` reads back as `null`
 * for non-recurring rows (all its columns are `NULL`), while a recurring row always has at least
 * [recurrenceBoundKind] set — so `null` ⇔ non-recurring is unambiguous.
 *
 * - [firstOccurrenceInstantMs]: absolute start of the earliest instance (low bound). Equals
 *   [EventTimingEntity.dtStartInstantMs] here (RRULE can't precede `DTSTART`); a preceding `RDATE`
 *   (PR 10) or an override moved earlier (PR 11) may lower it later. `null` for floating series
 *   (no absolute instant — the query falls back to the wall-clock [EventTimingEntity.dtStart]).
 * - [lastPossibleOccurrenceEndInstantMs]: absolute end of the last instance **plus its duration**
 *   (RFC 5545 §3.3.10 `UNTIL` bounds the last *start*, not the end) — see [recurrenceBoundKind].
 * - [lastOccurrenceEndLocalDateTime]: wall-clock pendant of the upper bound for floating/all-day
 *   series, whose absolute instant is `NULL`; the range query has a dedicated floating branch.
 * - [recurrenceBoundKind]: distinguishes `Finite` / `Infinite` / `FiniteDeferred` so a `NULL` bound
 *   instant is never ambiguous.
 */
internal data class RecurrenceBoundsEntity(
    val firstOccurrenceInstantMs: Long? = null,
    val lastPossibleOccurrenceEndInstantMs: Long? = null,
    val lastOccurrenceEndLocalDateTime: LocalDateTime? = null,
    val recurrenceBoundKind: RecurrenceBoundKind? = null,
)
