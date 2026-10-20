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

public data class EventAlarm(
    val action: AlarmAction,
    val trigger: AlarmTrigger,
    val description: String? = null,
    val summary: String? = null,
    val attendees: List<String> = emptyList(),
    val attachments: List<String> = emptyList(),
    /** `null` on the alarms the server never named. Carried back and forth untouched. */
    val uid: AlarmId.Uid? = null,
    /** Carried back and forth, never expanded into upcoming alarms. */
    val repetition: AlarmRepetition? = null,
) {

    /** The server's [uid] when it gave one, an [AlarmId.Local] otherwise, which is not unique. */
    public val id: AlarmId get() = uid ?: localAlarmId(action, trigger)
}

/** Written so two triggers firing alike read alike, `-PT15M` and `-PT900S` included. */
internal fun localAlarmId(action: AlarmAction, trigger: AlarmTrigger): AlarmId.Local {
    val canonical = when (trigger) {
        is AlarmTrigger.Relative -> "R:${trigger.offset.inWholeSeconds}:${trigger.relatedTo.name}"
        is AlarmTrigger.Absolute -> "A:${trigger.instant.epochSeconds}"
    }
    return AlarmId.Local("${action.toIcalString()}@$canonical")
}
