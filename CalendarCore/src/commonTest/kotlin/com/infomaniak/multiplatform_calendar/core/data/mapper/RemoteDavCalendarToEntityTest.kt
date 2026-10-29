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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavCalendar
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteDavCalendarToEntityTest {

    private val accountId = AccountId(1)

    @Test
    fun toEntitiesPreservingLocalPrefs_setsVisibilityTrueOnlyForDefaultCalendarOnCreation() {
        val remotes = listOf(
            RemoteDavCalendar(
                url = "https://dav.example/cal/default/",
                displayName = "Default",
                isDefaultCalendar = true,
            ),
            RemoteDavCalendar(
                url = "https://dav.example/cal/secondary/",
                displayName = "Secondary",
                isDefaultCalendar = false,
            ),
        )

        val entities = remotes.toEntitiesPreservingLocalPrefs(
            accountId = accountId,
            existingByCalendarId = emptyMap(),
        )

        val byId = entities.associateBy { it.id }
        assertTrue(byId.getValue(CalendarId("https://dav.example/cal/default/")).isVisible)
        assertFalse(byId.getValue(CalendarId("https://dav.example/cal/secondary/")).isVisible)
    }

    @Test
    fun toEntitiesPreservingLocalPrefs_keepsExistingVisibilityPreference() {
        val remote = RemoteDavCalendar(
            url = "https://dav.example/cal/default/",
            displayName = "Default",
            isDefaultCalendar = true,
        )
        val calendarId = CalendarId(remote.url)
        val existing = CalendarEntity(
            id = calendarId,
            accountId = accountId,
            displayName = "Default",
            color = null,
            isVisible = false,
            isDefaultCalendar = false,
        )

        val mapped = listOf(remote).toEntitiesPreservingLocalPrefs(
            accountId = accountId,
            existingByCalendarId = mapOf(calendarId to existing),
        ).single()

        assertEquals(calendarId, mapped.id)
        assertTrue(mapped.isDefaultCalendar)
        assertFalse(mapped.isVisible)
    }
}

