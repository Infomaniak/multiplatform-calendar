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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.NotificationTopicColumn
import com.infomaniak.multiplatform_calendar.core.data.local.relation.CalendarNotificationSettingsWithTopics
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.CalendarNotificationSettings
import com.infomaniak.multiplatform_calendar.core.domain.model.settings.NotificationTopic

internal fun CalendarNotificationSettingsWithTopics.toDomain() = CalendarNotificationSettings(
    isEnabled = notificationsEnabled,
    disabledTopics = disabledTopics.mapNotNullTo(mutableSetOf()) { it.topic.toNotificationTopic() },
)

internal val NotificationTopic.column: String
    get() = when (this) {
        NotificationTopic.Reminders -> NotificationTopicColumn.REMINDERS
    }

private fun String.toNotificationTopic(): NotificationTopic? = NotificationTopic.entries.firstOrNull { it.column == this }
