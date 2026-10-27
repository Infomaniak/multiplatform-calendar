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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceTarget
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ParticipationReply
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class EventRepositoryWriteReadBackTest : EventRepositoryTestBase() {

    private val eventId = EventId("https://cal/main/invite.ics")

    private suspend fun seedInvitation() {
        seedCalendar()
        val event = dailyMasterEntity(eventId, CALENDAR_ID).copy(rrule = null, hasRecurrence = false, recurrenceBounds = null)
        eventDao().upsert(listOf(EventWithRawIcs(event, "INVITE-ICS")))
        fakeCaldav.patchedEvent = remoteDavEvent(icsData = "REPLIED-ICS", summary = "Replied")
    }

    private suspend fun reply() = repository.updateParticipationStatus(
        CREDENTIALS,
        OccurrenceTarget.Single(OccurrenceId.Master(eventId)),
        ParticipationReply.Accepted,
    )

    @Test
    fun updateParticipationStatus_answeredWithoutEtag_storesTheVersionTheServerKept() = runTest {
        seedInvitation()
        fakeCaldav.updatedEtag = ""
        fakeCaldav.storedEvents = listOf(
            remoteDavEvent(icsData = "SERVER-ICS", summary = "Kept").copy(url = eventId.url, etag = "etag-server"),
        )

        reply()

        assertEquals(listOf(CALENDAR_ID.url to listOf(eventId.url)), fakeCaldav.fetches)
        val stored = eventDao().getEvent(eventId)!!
        assertEquals("etag-server", stored.etag)
        assertEquals("Kept", stored.content.summary)
        assertEquals("SERVER-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun updateParticipationStatus_answeredWithAnEtag_readsNothingBack() = runTest {
        seedInvitation()
        fakeCaldav.updatedEtag = "etag-2"

        reply()

        assertTrue(fakeCaldav.fetches.isEmpty())
        assertEquals("etag-2", eventDao().getEvent(eventId)!!.etag)
        assertEquals("REPLIED-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun updateParticipationStatus_whenTheReadBackFails_keepsWhatWasWritten() = runTest {
        seedInvitation()
        fakeCaldav.updatedEtag = ""
        fakeCaldav.fetchFailure = IllegalStateException("offline")

        reply()

        assertEquals("", eventDao().getEvent(eventId)!!.etag)
        assertEquals("REPLIED-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun createEvent_answeredWithoutEtag_storesTheVersionTheServerKept() = runTest {
        seedCalendar()
        val createdId = EventId("https://cal/main/new.ics")
        fakeCaldav.patchedEvent = remoteDavEvent(icsData = "BUILT-ICS", summary = "Fresh")
        fakeCaldav.createdRef = RemoteDavEventRef(url = createdId.url, etag = "")
        fakeCaldav.storedEvents = listOf(
            remoteDavEvent(icsData = "SERVER-ICS", summary = "Fresh").copy(url = createdId.url, etag = "etag-server"),
        )

        repository.createEvent(CREDENTIALS, editData(title = "Fresh"))

        assertEquals("etag-server", eventDao().getEvent(createdId)!!.etag)
        assertEquals("SERVER-ICS", eventDao().getRawIcs(createdId))
    }
}
