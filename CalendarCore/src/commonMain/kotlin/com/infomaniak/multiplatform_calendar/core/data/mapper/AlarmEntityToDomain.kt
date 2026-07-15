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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AlarmEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmTrigger
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal fun AlarmEntity.toDomain(): EventAlarm? {
    val trigger = when {
        triggerRelativeMillis != null -> AlarmTrigger.Relative(
            offset = triggerRelativeMillis.milliseconds,
            relatedTo = triggerRelatedTo,
        )
        triggerAbsoluteEpochMillis != null -> AlarmTrigger.Absolute(
            instant = Instant.fromEpochMilliseconds(triggerAbsoluteEpochMillis),
        )
        else -> return null
    }
    return EventAlarm(
        action = AlarmAction.fromIcalString(action),
        trigger = trigger,
        description = description,
        summary = summary,
        attendees = attendees,
        attach = attach,
    )
}
