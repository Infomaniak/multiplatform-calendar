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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmTrigger
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.alarmFiringKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.localAlarmId

/** The alarms of one VEVENT, those whose trigger cannot be read left out. */
internal fun List<AlarmEntity>.toDomain(): List<EventAlarm> = withDomain().map { it.second }

/** Each readable stored alarm of one VEVENT, paired with how the domain reads it. */
internal fun List<AlarmEntity>.withDomain(): List<Pair<AlarmEntity, EventAlarm>> {
    val ranks = HashMap<String, Int>()
    return mapNotNull { entity ->
        val trigger = entity.trigger() ?: return@mapNotNull null
        val action = AlarmAction.fromIcalString(entity.action)
        val id = entity.uid?.let(AlarmId::Uid) ?: run {
            val key = alarmFiringKey(action, trigger)
            val rank = ranks.getOrElse(key) { 0 }
            ranks[key] = rank + 1
            localAlarmId(action, trigger, rank)
        }
        entity to EventAlarm(action = action, trigger = trigger, id = id)
    }
}

private fun AlarmEntity.trigger(): AlarmTrigger? = when {
    triggerRelative != null -> AlarmTrigger.Relative(offset = triggerRelative, relatedTo = triggerRelatedTo)
    triggerAbsolute != null -> AlarmTrigger.Absolute(instant = triggerAbsolute)
    else -> null
}
