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

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId

/**
 * Local notification settings of a calendar, kept apart from [CalendarEntity] so that the calendar sync never
 * has to know about them. A calendar without a row is enabled.
 */
@Entity(
    tableName = "calendar_notification_settings",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CalendarNotificationSettingsEntity(
    @PrimaryKey val calendarId: CalendarId,
    /** One of [CalendarNotificationStateColumn]; an unknown value is read as enabled. */
    val state: String,
)

/** A topic a calendar whose [CalendarNotificationSettingsEntity.state] is `custom` notifies about. */
@Entity(
    tableName = "calendar_notification_topics",
    primaryKeys = ["calendarId", "topic"],
    foreignKeys = [
        ForeignKey(
            entity = CalendarNotificationSettingsEntity::class,
            parentColumns = ["calendarId"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CalendarNotificationTopicEntity(
    val calendarId: CalendarId,
    /** One of [NotificationTopicColumn]; an unknown value is ignored when read. */
    val topic: String,
)

internal object CalendarNotificationStateColumn {
    const val DISABLED = "disabled"
    const val CUSTOM = "custom"
}

internal object NotificationTopicColumn {
    const val REMINDERS = "reminders"
}
