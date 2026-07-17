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
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmTrigger
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.TriggerRelation
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAlarmEdit

/**
 * Returns `null` when the projected alarms match [previous], leaving source VALARM blocks untouched
 * so `X-*` / exotic params survive partial edits; otherwise the full replacement list.
 */
internal fun resolveAlarmEdits(
    alarms: List<EventAlarm>,
    previous: List<AlarmEntity>,
): List<RemoteAlarmEdit>? {
    val projected = alarms.map(EventAlarm::toEntity)
    return if (projected == previous) null else alarms.map(EventAlarm::toRemoteEdit)
}

internal fun EventAlarm.toEntity(): AlarmEntity {
    val (relative, absolute, relatedTo) = when (val alarmTrigger = trigger) {
        is AlarmTrigger.Relative -> Triple(alarmTrigger.offset, null, alarmTrigger.relatedTo)
        is AlarmTrigger.Absolute -> Triple(null, alarmTrigger.instant, TriggerRelation.Start)
    }
    return AlarmEntity(
        action = action.toIcalString(),
        triggerRelative = relative,
        triggerAbsolute = absolute,
        triggerRelatedTo = relatedTo,
        description = description,
        summary = summary,
        attendees = attendees,
        attachments = attachments,
    )
}

private fun EventAlarm.toRemoteEdit(): RemoteAlarmEdit {
    val (duration, absolute, related) = when (val alarmTrigger = trigger) {
        is AlarmTrigger.Relative -> Triple(
            alarmTrigger.offset.toIsoString(),
            null,
            if (alarmTrigger.relatedTo == TriggerRelation.End) "END" else "START",
        )
        is AlarmTrigger.Absolute -> Triple(null, alarmTrigger.instant.toICalUtcDateTime(), "START")
    }
    return RemoteAlarmEdit(
        action = action.toIcalString(),
        triggerDuration = duration,
        triggerAbsolute = absolute,
        triggerRelatedTo = related,
        description = description,
        summary = summary,
        attendees = attendees,
        attach = attachments,
    )
}

private fun AlarmAction.toIcalString(): String = when (this) {
    AlarmAction.Display -> "DISPLAY"
    AlarmAction.Audio -> "AUDIO"
    AlarmAction.Email -> "EMAIL"
    is AlarmAction.Unknown -> raw
}
