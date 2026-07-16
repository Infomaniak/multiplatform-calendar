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
package com.infomaniak.multiplatform_calendar.data.remote.caldav.model

/**
 * Edited VEVENT fields applied onto an existing iCS by [CalendarSyncRemoteSource.patchEventIcs].
 *
 * Forms of [dtStart]/[dtEnd] (RFC 5545):
 * - [allDay] = true                  → date only ("20260616"), [dtStartTzid]/[dtEndTzid] must be `null`.
 * - [dtStartTzid] / [dtEndTzid] set  → local wall-clock ("20260616T100000"), serialized with `TZID=<id>` (FORM #3).
 * - otherwise                        → UTC ("20260616T100000Z") (FORM #2).
 *
 * [stamp] is the UTC DTSTAMP/LAST-MODIFIED value.
 */
data class RemoteEventEdit(
    val summary: String?,
    val dtStart: String,
    val dtStartTzid: String?,
    val dtEnd: String?,
    val dtEndTzid: String?,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val timeZones: List<RemoteVTimeZone>,
    val colorChange: RemoteColorChange,
    val stamp: String,
)
