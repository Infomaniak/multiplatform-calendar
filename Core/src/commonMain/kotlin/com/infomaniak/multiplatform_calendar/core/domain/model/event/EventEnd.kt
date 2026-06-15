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

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How an event ends.
 *
 * RFC 5545 makes `DTEND` and `DURATION` mutually exclusive in a `VEVENT`: a component MUST NOT carry
 * both. This sealed hierarchy makes that exclusion unrepresentable-when-wrong at the type level —
 * an event either ends at an explicit instant ([At]) or lasts for a duration ([Lasting]), never both.
 *
 * The absence of an end is modeled by a `null` [EventEnd] (see [EventTiming.Timed.end]).
 */
@OptIn(ExperimentalTime::class)
public sealed interface EventEnd {

    /** Explicit end timestamp, from the iCal `DTEND` property. */
    public data class At(val instant: Instant) : EventEnd

    /** A duration relative to the event start, from the iCal `DURATION` property. */
    public data class Lasting(val duration: Duration) : EventEnd
}

