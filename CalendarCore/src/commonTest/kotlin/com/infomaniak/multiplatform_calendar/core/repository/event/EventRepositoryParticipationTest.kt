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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOccurrenceSlot
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteReplyScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

internal class EventRepositoryParticipationTest : EventRepositoryTestBase() {

    private val seriesId = EventId("https://cal/main/series.ics")
    private val sixteenth = LocalDateTime(2026, 6, 16, 10, 0)

    private suspend fun seedSeries() {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = seriesId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "SERIES-ICS")))
    }

    private fun recurring(scope: RecurrenceScope) = OccurrenceTarget.Recurring(occurrenceOf(seriesId, sixteenth), scope)

    @Test
    fun updateParticipationStatus_plainEvent_repliesOnTheWholeResourceWithTheAccountEmails() = runTest {
        seedCalendar()
        val event = dailyMasterEntity(EventId("https://cal/main/plain.ics"), CALENDAR_ID)
            .copy(rrule = null, hasRecurrence = false, recurrenceBounds = null)
        eventDao().upsert(listOf(EventWithRawIcs(event, "PLAIN-ICS")))

        repository.updateParticipationStatus(CREDENTIALS, OccurrenceTarget.Single(OccurrenceId.Master(event.id)), ParticipationReply.Tentative)

        val reply = fakeCaldav.replies.single()
        assertEquals("PLAIN-ICS", reply.icsData)
        assertEquals(RemoteReplyScope.Series, reply.scope)
        assertEquals(ACCOUNT_EMAILS, reply.emails)
        assertEquals("TENTATIVE", reply.partStat)
    }

    @Test
    fun updateParticipationStatus_allOccurrences_repliesOnTheSeries() = runTest {
        seedSeries()

        repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.AllOccurrences), ParticipationReply.Accepted)

        assertEquals(RemoteReplyScope.Series, fakeCaldav.replies.single().scope)
        assertEquals("ACCEPTED", fakeCaldav.replies.single().partStat)
    }

    @Test
    fun updateParticipationStatus_thisOccurrence_detachesTheInstanceOnItsSlot() = runTest {
        seedSeries()

        repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.ThisOccurrence), ParticipationReply.Declined)

        val scope = assertIs<RemoteReplyScope.Occurrence>(fakeCaldav.replies.single().scope)
        assertEquals(RemoteRecurrenceId(tzid = null, isDateOnly = false, value = "20260616T100000Z"), scope.recurrenceId)
        assertEquals(
            RemoteOccurrenceSlot(
                dtStart = "20260616T100000Z",
                dtStartTzid = null,
                dtEnd = "20260616T110000Z",
                dtEndTzid = null,
                allDay = false,
            ),
            scope.slot,
        )
        assertEquals("DECLINED", fakeCaldav.replies.single().partStat)
    }

    @Test
    fun updateParticipationStatus_thisOccurrence_addressesAnOverrideUnderTheFormItWasWrittenIn() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = seriesId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(seriesId, originalStart = sixteenth).copy(
            recurrenceIdValue = "20260616T120000",
            recurrenceIdTzid = "Europe/Zurich",
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "SERIES-ICS", listOf(override))))

        repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.ThisOccurrence), ParticipationReply.Accepted)

        val scope = assertIs<RemoteReplyScope.Occurrence>(fakeCaldav.replies.single().scope)
        assertEquals(RemoteRecurrenceId(tzid = "Europe/Zurich", isDateOnly = false, value = "20260616T120000"), scope.recurrenceId)
    }

    @Test
    fun updateParticipationStatus_thisOccurrence_leavesAnExcludedSlotAlone() = runTest {
        seedCalendar()
        val master = recurringColorMaster(
            eventId = seriesId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        ).copy(exDates = listOf(icalUtc(2026, 6, 16)))
        eventDao().upsert(listOf(EventWithRawIcs(master, "SERIES-ICS")))

        repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.ThisOccurrence), ParticipationReply.Accepted)

        assertEquals(emptyList(), fakeCaldav.calls)
    }

    @Test
    fun updateParticipationStatus_thisAndFollowing_isRefused() = runTest {
        seedSeries()

        assertFailsWith<IllegalArgumentException> {
            repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.ThisAndFollowing), ParticipationReply.Accepted)
        }
        assertEquals(emptyList(), fakeCaldav.calls)
    }
}
