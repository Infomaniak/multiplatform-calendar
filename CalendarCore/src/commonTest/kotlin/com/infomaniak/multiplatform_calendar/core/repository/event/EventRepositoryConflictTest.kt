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
package com.infomaniak.multiplatform_calendar.core.repository.event

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class EventRepositoryConflictTest : EventRepositoryTestBase() {

    @Test
    fun update_usesEtagFromSnapshotEvenWhenSyncRunsDuringPatch() = runTest {
        val eventId = EventId("https://cal/main/series.ics")
        seedCalendar()
        val entity = dailyMasterEntity(eventId, CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(entity, "LOCAL-ICS")))
        fakeCaldav.patchFromIcs = { ics, _ ->
            assertEquals("LOCAL-ICS", ics)
            eventDao().upsert(listOf(EventWithRawIcs(entity.copy(etag = "2"), "SERVER-ICS")))
            fakeCaldav.patchedEvent.copy(icsData = "PATCHED-ICS")
        }

        val data = checkNotNull(repository.getEditData(OccurrenceId.Master(eventId))).copy(title = "Renamed")
        repository.updateEvent(CREDENTIALS, CREDENTIALS, eventId, data)

        assertEquals(listOf("LOCAL-ICS"), fakeCaldav.patchInputs)
        assertEquals(listOf("1" to "PATCHED-ICS"), fakeCaldav.updates)
    }
}
