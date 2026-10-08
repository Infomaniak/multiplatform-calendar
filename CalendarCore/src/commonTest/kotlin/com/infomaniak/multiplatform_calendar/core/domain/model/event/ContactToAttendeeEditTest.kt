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

import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactToAttendeeEditTest {

    @Test
    fun aContactIsInvitedUnderItsEmailAndName() {
        val contact = Contact(email = "alice@example.com", name = "Alice", avatar = null, comesFromApi = true)

        assertEquals(
            AttendeeEdit(email = "alice@example.com", displayName = "Alice", role = AttendeeRole.Requested),
            contact.toAttendeeEdit(),
        )
    }

    @Test
    fun aContactWithoutNameIsInvitedWithoutDisplayName() {
        val contact = Contact(email = "alice@example.com", name = " ", avatar = null, comesFromApi = false)

        assertEquals(null, contact.toAttendeeEdit().displayName)
    }
}
