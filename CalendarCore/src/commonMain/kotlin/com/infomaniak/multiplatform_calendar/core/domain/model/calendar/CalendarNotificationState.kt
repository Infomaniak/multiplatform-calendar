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
package com.infomaniak.multiplatform_calendar.core.domain.model.calendar

/**
 * Which notifications a calendar may send, provided its account allows notifications at all.
 *
 * Enforced by the SDK: `CalendarManager.observeUpcomingAlarms` only hands out the alarms allowed to notify.
 */
public sealed interface CalendarNotificationState {
    /** Every [NotificationTopic] notifies; the state of a calendar never configured. */
    public data object Enabled : CalendarNotificationState

    /** Nothing notifies. */
    public data object Disabled : CalendarNotificationState

    /** Only [topics] notify; an empty set notifies nothing. */
    public data class Custom(val topics: Set<NotificationTopic>) : CalendarNotificationState

    public fun allows(topic: NotificationTopic): Boolean = when (this) {
        Enabled -> true
        Disabled -> false
        is Custom -> topic in topics
    }
}

/** A kind of notification, toggled one by one by [CalendarNotificationState.Custom]. */
public enum class NotificationTopic {
    /** The alarms of the events, as handed out by `CalendarManager.observeUpcomingAlarms`. */
    Reminders,
}
