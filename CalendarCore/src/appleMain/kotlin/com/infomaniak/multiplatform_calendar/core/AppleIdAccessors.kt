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
package com.infomaniak.multiplatform_calendar.core

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event

/**
 * Apple-only accessors that expose the underlying values of the `value class` ids as plain types.
 *
 * Kotlin value classes are boxed to `Any` when exported as *properties* to Swift/ObjC, which makes
 * `calendar.id` and the event ID value-class properties are unusable from Swift. These extensions
 * surface their wrapped values while
 * keeping the value classes intact in common code and on Android.
 */
public val Calendar.idValue: String get() = id.url
public val Calendar.accountIdValue: Long get() = accountId.value

/**
 * Persistent event identifier (master DB/CalDAV id).
 *
 * For recurring events this stays the same across all occurrences and is the value to use for
 * read/write operations (`observeEvent`, `updateEvent`, `deleteEvent`, ...).
 */
public val Event.masterEventIdValue: String get() = masterEventId.url

/**
 * Display occurrence identifier.
 *
 * Non-recurring events: same value as [masterEventIdValue].
 * Recurring events: synthetic value `"<masterEventId>#<canonicalOccurrenceKey>"` unique per occurrence.
 */
public val Event.occurrenceIdValue: String get() = occurrenceId.value
public val Event.calendarIdValue: String get() = calendarId.url
public val Event.accountIdValue: Long get() = accountId.value
