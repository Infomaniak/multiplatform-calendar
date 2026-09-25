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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.AlarmAction
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm

/** What an edit does to the `VALARM`s already on the resource, mirroring [DateListEdit] for alarms. */
public sealed interface AlarmListEdit {

    /**
     * Leave the resource's own `VALARM`s alone.
     *
     * The alarms an edit could carry are a *domain* projection, which drops any stored alarm whose
     * trigger cannot be read; replacing the list from that projection would delete such an alarm from
     * the resource. An edit that is not about alarms says so here and cannot lose one.
     */
    public data object Preserve : AlarmListEdit

    /**
     * Make the resource's alarms be exactly [alarms], the ones to keep included.
     *
     * This is a whole-list replacement, not a set of additions: the `VALARM`s are stripped and rewritten
     * from [alarms]. When the list projects back to what is already stored, nothing is emitted at all and
     * the original blocks survive untouched, exotic `X-*` parameters and all.
     *
     * [AlarmAction.Unknown] alarms are outside its reach: stated ones are ignored, stored ones kept verbatim.
     */
    public data class Replace(val alarms: List<EventAlarm>) : AlarmListEdit
}

/** The alarms this edit states, none when it states none — see [AlarmListEdit.Preserve]. */
internal val AlarmListEdit.statedAlarms: List<EventAlarm>
    get() = when (this) {
        AlarmListEdit.Preserve -> emptyList()
        is AlarmListEdit.Replace -> alarms
    }
