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
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class EventRepositoryIcsTest : EventRepositoryTestBase() {

    private val masterId = EventId("https://cal/main/series.ics")

    private suspend fun seedSeries() {
        seedCalendar()
        eventDao().upsert(listOf(EventWithRawIcs(dailyMasterEntity(masterId, CALENDAR_ID), SERIES_ICS)))
    }

    @Test
    fun getIcs_ofTheMaster_returnsTheStoredResource() = runTest {
        seedSeries()

        assertEquals(SERIES_ICS, repository.getIcs(OccurrenceId.Master(masterId)))
    }

    @Test
    fun getIcs_ofAnOccurrence_returnsTheWholeSeriesResource() = runTest {
        seedSeries()

        assertEquals(SERIES_ICS, repository.getIcs(occurrenceOf(masterId, LocalDateTime(2026, 6, 16, 10, 0))))
    }

    @Test
    fun getIcs_ofAnUnknownEvent_returnsNull() = runTest {
        seedSeries()

        assertNull(repository.getIcs(OccurrenceId.Master(EventId("https://cal/main/unknown.ics"))))
    }

    private companion object {
        const val SERIES_ICS = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nRRULE:FREQ=DAILY\nEND:VEVENT\nEND:VCALENDAR"
    }
}
