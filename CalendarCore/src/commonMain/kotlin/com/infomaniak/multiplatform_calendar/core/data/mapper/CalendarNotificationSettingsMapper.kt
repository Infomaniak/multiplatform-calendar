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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarNotificationSettingsEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarNotificationStateColumn
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarNotificationTopicEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.NotificationTopicColumn
import com.infomaniak.multiplatform_calendar.core.data.local.relation.CalendarNotificationSettingsWithTopics
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarNotificationState
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.NotificationTopic

/** A calendar without settings, `null` here, is enabled. */
internal fun CalendarNotificationSettingsWithTopics?.toDomain(): CalendarNotificationState = when (this?.settings?.state) {
    CalendarNotificationStateColumn.DISABLED -> CalendarNotificationState.Disabled
    CalendarNotificationStateColumn.CUSTOM -> CalendarNotificationState.Custom(topics.mapNotNullTo(mutableSetOf()) { it.topic.toTopic() })
    else -> CalendarNotificationState.Enabled
}

/** The row storing this state for [calendarId]; `null` when enabled, which is stored as no row at all. */
internal fun CalendarNotificationState.toSettingsEntity(calendarId: CalendarId): CalendarNotificationSettingsEntity? {
    val state = when (this) {
        CalendarNotificationState.Enabled -> return null
        CalendarNotificationState.Disabled -> CalendarNotificationStateColumn.DISABLED
        is CalendarNotificationState.Custom -> CalendarNotificationStateColumn.CUSTOM
    }
    return CalendarNotificationSettingsEntity(calendarId, state)
}

internal fun CalendarNotificationState.topicEntities(calendarId: CalendarId): List<CalendarNotificationTopicEntity> =
    (this as? CalendarNotificationState.Custom)?.topics.orEmpty().map { CalendarNotificationTopicEntity(calendarId, it.column) }

private val NotificationTopic.column: String
    get() = when (this) {
        NotificationTopic.Reminders -> NotificationTopicColumn.REMINDERS
    }

private fun String.toTopic(): NotificationTopic? = NotificationTopic.entries.firstOrNull { it.column == this }
