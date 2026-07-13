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

import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration

/**
 * The full timing of an [EventEntity] (RFC 5545), grouped as a Room `@Embedded` sub-type.
 *
 * Embedding (rather than inlining these columns directly on [EventEntity]) keeps the ~10 tightly
 * coupled timing fields together, encapsulates their invariants, and lets a future
 * `EventOverrideEntity` (RECURRENCE-ID overrides for recurring events) reuse the exact same shape.
 * With no column prefix the fields keep their original column names, so this is a pure code-level
 * grouping: no SQL migration, no DAO impact, indices on [dtStartInstantMs] / [dtEndInstantMs] stay valid.
 */
internal data class EventTimingEntity(
    val dtStart: LocalDateTime,
    val dtEnd: LocalDateTime? = null,
    val duration: Duration? = null,
    // Resolved end (single source of truth, see `resolveEffectiveEnd`): used as the domain timing's end
    // and as the wall-clock pendant of [dtEndInstantMs]. SQL can't derive it from ISO-text storage.
    val dtEndEffective: LocalDateTime,
    /**
     * IANA TZID anchoring `DTSTART` (e.g. "Europe/Paris"), or `null` for `VALUE=DATE` all-day
     * events and floating (no `TZID`, no `Z`) `DATE-TIME` events. UTC events store `"UTC"`.
     */
    val startTimeZone: String? = null,
    /**
     * IANA TZID anchoring `DTEND`. Same semantics as [startTimeZone].
     *
     * Kept independent from [startTimeZone] because RFC 5545 §3.8.2.2 allows `DTEND` to reference
     * a different `TZID` than `DTSTART` (e.g. a flight "9:00 America/New_York → 16:00 Europe/Paris").
     * For all-day events both zones are `null`.
     */
    val endTimeZone: String? = null,
    /**
     * Absolute (UTC) start in epoch milliseconds, resolved from [dtStart] + [startTimeZone] at insertion time.
     * Indexed alongside [dtEndInstantMs] and used by [com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao.observeVisibleInRange]
     * so range-overlap queries stay correct across mixed time-zones (the wall-clock columns are display-only).
     *
     * `null` for **floating** DATE-TIME events (RFC 5545 FORM #1: no `TZID`, no `Z`): a floating event
     * has no absolute instant by definition — its wall-clock is meant to be interpreted in the recipient's
     * *current* local zone at display time. Anchoring at insertion would go stale on device zone change
     * (travel, DST), so we don't. Range queries fall back to a wall-clock branch for these rows.
     * For `VALUE=DATE` (all-day) the start of the day is taken in UTC so the value is device-independent.
     */
    val dtStartInstantMs: Long?,
    /** Absolute (UTC) end in epoch milliseconds, resolved from [dtEndEffective] + [endTimeZone]. See [dtStartInstantMs] (same `null` rule per end). */
    val dtEndInstantMs: Long?,
    val isAllDay: Boolean = false,
)
