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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours

/**
 * How far from its event's start the alarms of one row may go off, as a Room `@Embedded` on
 * [EventContentEntity] so both an event and a `RECURRENCE-ID` override carry their own. Alarms have no
 * table of their own, so without these columns a query cannot tell how far past its window to read; see
 * [EventDao.observeAlarmedInRange][com.infomaniak.multiplatform_calendar.core.data.local.dao.EventDao.observeAlarmedInRange].
 *
 * - [minAlarmOffsetMs] / [maxAlarmOffsetMs]: smallest and largest *relative* trigger, counted from the
 *   occurrence's **start** — a `RELATED=END` one is normalised by adding the event's duration. Negative
 *   means "before".
 * - [minAbsoluteAlarmMs] / [maxAbsoluteAlarmMs]: smallest and largest `TRIGGER;VALUE=DATE-TIME`.
 *
 * All four `NULL` means the row holds no alarm, which the query drops outright.
 */
internal data class AlarmBoundsEntity(
    val minAlarmOffsetMs: Long? = null,
    val maxAlarmOffsetMs: Long? = null,
    val minAbsoluteAlarmMs: Long? = null,
    val maxAbsoluteAlarmMs: Long? = null,
) {
    internal companion object {
        /** The largest clock change any zone applies at once, hence the widest a zoneless event can stretch. */
        private val DST_SLACK_MS = 2.hours.inWholeMilliseconds

        /**
         * The bounds of [alarms], for an event happening at [timing].
         *
         * Mirrors [AlarmEntityToDomain][com.infomaniak.multiplatform_calendar.core.data.mapper.toDomain]:
         * a relative trigger wins over an absolute one, and an alarm carrying neither is skipped the same
         * way the projection skips it.
         */
        fun of(alarms: List<AlarmEntity>, timing: EventTimingEntity): AlarmBoundsEntity {
            var minOffset: Long? = null
            var maxOffset: Long? = null
            var minAbsolute: Long? = null
            var maxAbsolute: Long? = null

            for (alarm in alarms) {
                val relative = alarm.triggerRelative
                if (relative != null) {
                    val fromStart = when (alarm.triggerRelatedTo) {
                        TriggerRelation.Start -> relative.inWholeMilliseconds
                        TriggerRelation.End -> timing.durationMs() + relative.inWholeMilliseconds
                    }
                    // A zoneless row's duration is only known once a reader's zone is picked.
                    val slack = when {
                        alarm.triggerRelatedTo == TriggerRelation.End && timing.startTimeZone == null -> DST_SLACK_MS
                        else -> 0L
                    }
                    val low = fromStart - slack
                    val high = fromStart + slack
                    minOffset = minOf(minOffset ?: low, low)
                    maxOffset = maxOf(maxOffset ?: high, high)
                } else {
                    val absolute = alarm.triggerAbsolute?.toEpochMilliseconds() ?: continue
                    minAbsolute = minOf(minAbsolute ?: absolute, absolute)
                    maxAbsolute = maxOf(maxAbsolute ?: absolute, absolute)
                }
            }

            return AlarmBoundsEntity(minOffset, maxOffset, minAbsolute, maxAbsolute)
        }
    }
}

/**
 * How long the event lasts, which a `RELATED=END` trigger is counted from. Absolute instants are
 * preferred, as `DTEND` may reference another zone than `DTSTART` (RFC 5545 §3.8.2.2) and only they
 * account for it. A zoneless row falls back to its wall-clock difference, which a clock change can
 * make shorter — hence the slack [AlarmBoundsEntity.of] pads it with.
 */
private fun EventTimingEntity.durationMs(): Long {
    val start = dtStartInstantMs
    val end = dtEndInstantMs
    if (start != null && end != null) return end - start

    return dtEndEffective.toInstant(TimeZone.UTC).toEpochMilliseconds() -
        dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds()
}
