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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertNull

internal class EventRepositoryConflictTest : EventRepositoryTestBase() {

    private val eventId = EventId("https://cal/main/series.ics")

    private fun conflict(operation: String = "Delete") = CaldavHttpException(
        statusCode = HttpStatusCode.PreconditionFailed.value,
        operation = operation,
        message = "Precondition Failed",
        cause = null,
    )

    private suspend fun seedEvent() {
        seedCalendar()
        eventDao().upsert(listOf(EventWithRawIcs(dailyMasterEntity(eventId, CALENDAR_ID), "LOCAL-ICS")))
    }

    private fun serverEvent(): RemoteDavEvent {
        val base = remoteDavEvent(icsData = "SERVER-ICS", summary = "Daily 10-11", rrule = "FREQ=DAILY;COUNT=5")
        return base.copy(
            url = eventId.url,
            etag = "2",
            content = base.content.copy(description = "Remote description"),
        )
    }

    private suspend fun renameEvent() {
        val data = checkNotNull(repository.getEditData(OccurrenceId.Master(eventId))).copy(title = "Renamed")
        repository.updateEvent(CREDENTIALS, CREDENTIALS, eventId, data)
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

    @Test
    fun update_retryRebuildsPatchFromRefreshedIcsAndPreservesRemoteFields() = runTest {
        seedEvent()
        val remote = serverEvent()
        fakeCaldav.storedEvents = listOf(remote)
        fakeCaldav.updateFailures += conflict("Update")
        fakeCaldav.updatedEtag = "3"
        fakeCaldav.patchFromIcs = { ics, edit ->
            if (ics == "LOCAL-ICS") {
                assertNull(edit.description)
            } else {
                assertEquals("SERVER-ICS", ics)
                assertEquals("Remote description", edit.description)
            }
            remote.copy(
                icsData = "$ics-PATCHED",
                content = remote.content.copy(summary = edit.summary, description = edit.description),
            )
        }

        renameEvent()

        assertEquals(listOf("1" to "LOCAL-ICS-PATCHED", "2" to "SERVER-ICS-PATCHED"), fakeCaldav.updates)
        assertEquals(listOf("LOCAL-ICS", "SERVER-ICS"), fakeCaldav.patchInputs)
        assertEquals("SERVER-ICS-PATCHED", eventDao().getRawIcs(eventId))
        assertEquals("Remote description", eventDao().getEvent(eventId)?.content?.description)
    }

    @Test
    fun update_doesNotRetryASecondPreconditionFailure() = runTest {
        seedEvent()
        val secondConflict = conflict("Update")
        fakeCaldav.storedEvents = listOf(serverEvent())
        fakeCaldav.updateFailures += conflict("Update")
        fakeCaldav.updateFailures += secondConflict

        assertSame(secondConflict, assertFailsWith<CaldavHttpException> { renameEvent() })

        assertEquals(2, fakeCaldav.updates.size)
        assertEquals(1, fakeCaldav.fetches.size)
        assertEquals("SERVER-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun update_missingRemoteRemainsAConflict() = runTest {
        seedEvent()
        val failure = conflict("Update")
        fakeCaldav.updateFailures += failure

        assertSame(failure, assertFailsWith<CaldavHttpException> { renameEvent() })

        assertEquals(1, fakeCaldav.updates.size)
        assertNotNull(eventDao().getEvent(eventId))
    }
}
