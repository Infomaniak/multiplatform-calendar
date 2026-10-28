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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CaldavHttpException
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavAttendee
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventOverride
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteIcalDateValueType
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteOccurrenceSlot
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceId
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteReplyScope
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

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

    @Test
    fun updateParticipationStatus_preconditionFailed_answersAgainOnTheVersionTheServerHolds() = runTest {
        val event = seedPlainEvent()
        fakeCaldav.updateFailures += preconditionFailed()
        fakeCaldav.storedEvents = listOf(serverEvent(event.id))

        repository.updateParticipationStatus(CREDENTIALS, OccurrenceTarget.Single(OccurrenceId.Master(event.id)), ParticipationReply.Declined)

        assertEquals(listOf("PLAIN-ICS", "SERVER-ICS"), fakeCaldav.replies.map { it.icsData })
        assertEquals(listOf(event.etag, "etag-server"), fakeCaldav.updates.map { it.first })
        assertEquals("etag-server", eventDao().getEvent(event.id)!!.etag)
    }

    @Test
    fun updateParticipationStatus_preconditionFailedOnceTheUserWasRemoved_keepsTheServerVersionWithoutAnsweringAgain() = runTest {
        val event = seedPlainEvent()
        fakeCaldav.updateFailures += preconditionFailed()
        fakeCaldav.storedEvents = listOf(serverEvent(event.id, attendees = emptyList()))

        repository.updateParticipationStatus(CREDENTIALS, OccurrenceTarget.Single(OccurrenceId.Master(event.id)), ParticipationReply.Declined)

        assertEquals(1, fakeCaldav.updates.size)
        assertEquals("etag-server", eventDao().getEvent(event.id)!!.etag)
    }

    @Test
    fun updateParticipationStatus_preconditionFailedTwice_fails() = runTest {
        val event = seedPlainEvent()
        val second = preconditionFailed()
        fakeCaldav.updateFailures += listOf(preconditionFailed(), second)
        fakeCaldav.storedEvents = listOf(serverEvent(event.id))

        val failure = assertFailsWith<CaldavHttpException> {
            repository.updateParticipationStatus(CREDENTIALS, OccurrenceTarget.Single(OccurrenceId.Master(event.id)), ParticipationReply.Declined)
        }

        assertSame(second, failure)
        assertEquals(2, fakeCaldav.updates.size)
    }

    @Test
    fun updateParticipationStatus_preconditionFailedAndNothingReadBack_fails() = runTest {
        val event = seedPlainEvent()
        val failure = preconditionFailed()
        fakeCaldav.updateFailures += failure

        val thrown = assertFailsWith<CaldavHttpException> {
            repository.updateParticipationStatus(CREDENTIALS, OccurrenceTarget.Single(OccurrenceId.Master(event.id)), ParticipationReply.Declined)
        }

        assertSame(failure, thrown)
        assertEquals(1, fakeCaldav.updates.size)
    }

    @Test
    fun updateParticipationStatus_preconditionFailedAndReadFailing_reportsTheReadFailureWithTheConflict() = runTest {
        val event = seedPlainEvent()
        val failure = preconditionFailed()
        val readFailure = IllegalStateException("read failed")
        fakeCaldav.updateFailures += failure
        fakeCaldav.fetchFailure = readFailure

        val thrown = assertFailsWith<CaldavHttpException> {
            repository.updateParticipationStatus(CREDENTIALS, OccurrenceTarget.Single(OccurrenceId.Master(event.id)), ParticipationReply.Declined)
        }

        assertSame(failure, thrown)
        assertEquals(listOf<Throwable>(readFailure), thrown.suppressedExceptions)
        assertEquals(1, fakeCaldav.updates.size)
    }

    @Test
    fun updateParticipationStatus_otherHttpFailure_isNotRetried() = runTest {
        val event = seedPlainEvent()
        fakeCaldav.updateFailures += CaldavHttpException(statusCode = HttpStatusCode.Forbidden.value, operation = "Update", message = "Forbidden", cause = null)

        assertFailsWith<CaldavHttpException> {
            repository.updateParticipationStatus(CREDENTIALS, OccurrenceTarget.Single(OccurrenceId.Master(event.id)), ParticipationReply.Declined)
        }

        assertEquals(1, fakeCaldav.updates.size)
        assertEquals(emptyList(), fakeCaldav.fetches)
    }

    @Test
    fun updateParticipationStatus_thisOccurrencePreconditionFailed_answersOnTheTimingTheServerHolds() = runTest {
        seedSeries()
        fakeCaldav.updateFailures += preconditionFailed()
        fakeCaldav.storedEvents = listOf(serverSeries(dtend = "20260615T120000Z", rrule = "FREQ=DAILY;COUNT=3"))

        repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.ThisOccurrence), ParticipationReply.Declined)

        val retry = fakeCaldav.replies.last()
        assertEquals("SERVER-ICS", retry.icsData)
        assertEquals("20260616T120000Z", assertIs<RemoteReplyScope.Occurrence>(retry.scope).slot.dtEnd)
    }

    @Test
    fun updateParticipationStatus_allOccurrencesPreconditionFailed_answersAgainWhenOnlyAnInstanceInvitesTheUser() = runTest {
        seedSeries()
        fakeCaldav.updateFailures += preconditionFailed()
        val series = serverSeries(dtend = "20260615T110000Z", rrule = "FREQ=DAILY;COUNT=3")
        val invitedInstance = RemoteDavEventOverride(
            recurrenceId = "20260616T100000Z",
            recurrenceIdTzid = null,
            recurrenceIdValueType = RemoteIcalDateValueType.DateTime,
            isThisAndFuture = false,
            content = series.content,
        )
        fakeCaldav.storedEvents = listOf(
            series.copy(content = series.content.copy(attendees = emptyList()), overrides = listOf(invitedInstance)),
        )

        repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.AllOccurrences), ParticipationReply.Accepted)

        assertEquals(2, fakeCaldav.updates.size)
        assertEquals(RemoteReplyScope.Series, fakeCaldav.replies.last().scope)
    }

    @Test
    fun updateParticipationStatus_thisOccurrenceGoneFromTheServer_fails() = runTest {
        seedSeries()
        val failure = preconditionFailed()
        fakeCaldav.updateFailures += failure
        fakeCaldav.storedEvents = listOf(serverSeries(dtend = "20260615T110000Z", rrule = "FREQ=DAILY;COUNT=1"))

        val thrown = assertFailsWith<CaldavHttpException> {
            repository.updateParticipationStatus(CREDENTIALS, recurring(RecurrenceScope.ThisOccurrence), ParticipationReply.Declined)
        }

        assertSame(failure, thrown)
        assertEquals(1, fakeCaldav.updates.size)
    }

    private fun serverSeries(dtend: String, rrule: String) =
        remoteDavEvent(icsData = "SERVER-ICS", dtstart = "20260615T100000Z", dtend = dtend, rrule = rrule, attendees = listOf(USER))
            .copy(url = seriesId.url, etag = "etag-server")

    private fun serverEvent(eventId: EventId, attendees: List<RemoteDavAttendee> = listOf(USER)) =
        remoteDavEvent(icsData = "SERVER-ICS", attendees = attendees).copy(url = eventId.url, etag = "etag-server")

    private suspend fun seedPlainEvent() = dailyMasterEntity(EventId("https://cal/main/plain.ics"), CALENDAR_ID)
        .copy(rrule = null, hasRecurrence = false, recurrenceBounds = null, etag = "etag-local")
        .also {
            seedCalendar()
            eventDao().upsert(listOf(EventWithRawIcs(it, "PLAIN-ICS")))
        }

    private fun preconditionFailed() =
        CaldavHttpException(statusCode = HttpStatusCode.PreconditionFailed.value, operation = "Update", message = "Precondition Failed", cause = null)

    private companion object {
        val USER = RemoteDavAttendee(
            email = ACCOUNT_EMAILS.first(),
            displayName = null,
            status = "NEEDS-ACTION",
            role = "REQ-PARTICIPANT",
            userType = "INDIVIDUAL",
            responseNeeded = true,
        )
    }
}
