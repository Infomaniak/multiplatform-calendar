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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.OrganizerEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeRole
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAttendeeEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteAttendeesChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOrganizerChange

internal fun AttendeeEntity.toEdit(): AttendeeEdit = AttendeeEdit(email = email, displayName = displayName, role = role)

/** Attendees stated as stored are [kept][RemoteAttendeeEdit.Kept], so their unmodelled parameters survive. */
internal fun EventEditData.resolveAttendeesChange(previous: List<AttendeeEntity>): RemoteAttendeesChange {
    val stated = attendees.distinctBy { it.email.lowercase() }
    val stored = previous.map(AttendeeEntity::toEdit)
    if (stated == stored) return RemoteAttendeesChange.Unchanged

    requireOrganizerForAttendees()
    val storedByEmail = stored.associateBy { it.email.lowercase() }
    return RemoteAttendeesChange.Set(
        stated.map { attendee ->
            if (storedByEmail[attendee.email.lowercase()] == attendee) {
                RemoteAttendeeEdit.Kept(attendee.email)
            } else {
                RemoteAttendeeEdit.Written(attendee.email, attendee.displayName, attendee.role.toIcal())
            }
        },
    )
}

internal fun EventEditData.resolveOrganizerChange(previous: OrganizerEntity?): RemoteOrganizerChange {
    if (organizer == previous?.toDomain()) return RemoteOrganizerChange.Unchanged

    requireOrganizerForAttendees()
    return when (organizer) {
        null -> RemoteOrganizerChange.Cleared
        else -> RemoteOrganizerChange.Set(organizer.email, organizer.displayName)
    }
}

/** An event with attendees has an organizer (RFC 5546 §3.2). */
private fun EventEditData.requireOrganizerForAttendees() {
    require(organizer != null || attendees.isEmpty()) { "An event with attendees needs an organizer" }
}

private fun AttendeeRole.toIcal(): String = when (this) {
    AttendeeRole.Chair -> "CHAIR"
    AttendeeRole.Requested -> "REQ-PARTICIPANT"
    AttendeeRole.Optional -> "OPT-PARTICIPANT"
    AttendeeRole.NonParticipant -> "NON-PARTICIPANT"
}
