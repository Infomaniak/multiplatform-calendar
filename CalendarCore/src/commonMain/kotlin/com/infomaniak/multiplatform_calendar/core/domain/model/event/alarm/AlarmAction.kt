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

/** RFC 5545 §3.8.6 alarm action. Unknown actions surface as [Unknown] so we don't drop them. */
public sealed interface AlarmAction {
    public object Display : AlarmAction
    public object Audio : AlarmAction
    public object Email : AlarmAction
    public data class Unknown(val raw: String) : AlarmAction

    public companion object {
        public fun fromIcalString(raw: String): AlarmAction = when (raw.uppercase()) {
            "DISPLAY" -> Display
            "AUDIO" -> Audio
            "EMAIL" -> Email
            else -> Unknown(raw.uppercase())
        }
    }
}
