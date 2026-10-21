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

/**
 * An alarm of an event, reduced to what the apps edit.
 *
 * [id] names the stored alarm this one stands for, `null` for a new one: an edit takes the `VALARM`
 * properties not exposed here (description, summary, attendees, attachments, repetition) back from it.
 */
public data class EventAlarm(
    val action: AlarmAction,
    val trigger: AlarmTrigger,
    val id: AlarmId? = null,
)

/** Written so two triggers firing alike read alike, `-PT15M` and `-PT900S` included. */
internal fun alarmFiringKey(action: AlarmAction, trigger: AlarmTrigger): String {
    val canonical = when (trigger) {
        is AlarmTrigger.Relative -> "R:${trigger.offset.inWholeSeconds}:${trigger.relatedTo.name}"
        is AlarmTrigger.Absolute -> "A:${trigger.instant.epochSeconds}"
    }
    return "${action.toIcalString()}@$canonical"
}

/** [rank] tells apart the alarms of one VEVENT firing alike, counted in the order they are stored. */
internal fun localAlarmId(action: AlarmAction, trigger: AlarmTrigger, rank: Int): AlarmId.Local {
    return AlarmId.Local("${alarmFiringKey(action, trigger)}#$rank")
}
