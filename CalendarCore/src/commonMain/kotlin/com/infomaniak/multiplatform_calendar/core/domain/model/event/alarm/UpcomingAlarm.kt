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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm

import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.jvm.JvmInline
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/**
 * Identity of a single alarm *firing*, stable for as long as that firing is, so a client can diff two
 * consecutive lists and only touch what moved.
 *
 * It is derived, never stored: the same firing read twice yields the same id without anything being
 * persisted, and nothing is ever written back to the server under it.
 *
 * Deliberately built on the moment the alarm goes off rather than on its offset: were it keyed on the
 * offset, moving an event would leave the id untouched (a recurring occurrence keeps the `RECURRENCE-ID`
 * of the slot it was generated on, moved or not) and a client comparing ids would keep a reminder
 * standing at the old time.
 */
@JvmInline
public value class UpcomingAlarmId internal constructor(public val value: String)

/** One alarm of [event] about to go off, at [firesAt]. */
@OptIn(ExperimentalObjCRefinement::class)
public data class UpcomingAlarm(
    @HiddenFromObjC
    val id: UpcomingAlarmId,
    val firesAt: Instant,
    val alarm: EventAlarm,
    val event: Event,
)
