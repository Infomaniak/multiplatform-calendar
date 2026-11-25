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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CaldavHttpException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class EventRepositoryConflictTest : EventRepositoryTestBase() {

    private val eventId = EventId("https://cal/main/series.ics")

    private fun conflict() = CaldavHttpException(
        statusCode = HttpStatusCode.PreconditionFailed.value,
        operation = "Delete",
        message = "Precondition Failed",
        cause = null,
    )

    private suspend fun seedEvent() {
        seedCalendar()
        eventDao().upsert(listOf(EventWithRawIcs(dailyMasterEntity(eventId, CALENDAR_ID), "LOCAL-ICS")))
    }

    @Test
    fun update_usesEtagFromSnapshotEvenWhenSyncRunsDuringPatch() = runTest {
        seedEvent()
        val entity = checkNotNull(eventDao().getEvent(eventId))
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

    @Test
    fun delete_missingRemoteIsSuccessAndRemovesLocalEvent() = runTest {
        seedEvent()
        val failure = conflict()
        fakeCaldav.deleteFailures += failure

        repository.deleteEvent(CREDENTIALS, eventId)

        assertEquals(listOf(eventId.url to "1"), fakeCaldav.deletes)
        assertEquals(1, fakeCaldav.fetches.size)
        assertNull(eventDao().getEvent(eventId))
    }

    @Test
    fun delete_fetchFailurePreservesConflictAndLocalEvent() = runTest {
        seedEvent()
        val conflict = conflict()
        fakeCaldav.deleteFailures += conflict
        fakeCaldav.fetchFailure = IllegalStateException("Network unavailable")

        assertSame(conflict, assertFailsWith<CaldavHttpException> { repository.deleteEvent(CREDENTIALS, eventId) })

        assertNotNull(eventDao().getEvent(eventId))
    }

    @Test
    fun delete_fetchCancellationPropagatesAndPreservesLocalEvent() = runTest {
        seedEvent()
        fakeCaldav.deleteFailures += conflict()
        fakeCaldav.fetchFailure = CancellationException("Cancelled")

        assertFailsWith<CancellationException> { repository.deleteEvent(CREDENTIALS, eventId) }

        assertNotNull(eventDao().getEvent(eventId))
    }
}
