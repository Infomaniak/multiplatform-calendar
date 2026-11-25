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
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceTarget
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CaldavHttpException
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventOverride
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDateListChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteIcalDateValue
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteIcalDateValueType
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class EventRepositoryConflictTest : EventRepositoryTestBase() {

    private val eventId = EventId("https://cal/main/series.ics")

    private fun conflict(operation: String = "Update") = CaldavHttpException(
        statusCode = HttpStatusCode.PreconditionFailed.value,
        operation = operation,
        message = "Precondition Failed",
        cause = null,
    )

    private suspend fun seedSeries() {
        seedCalendar()
        val master = dailyMasterEntity(eventId, CALENDAR_ID)
        eventDao().upsert(listOf(EventWithRawIcs(master, "LOCAL-ICS")))
    }

    private fun serverSeries() = remoteDavEvent(
        icsData = "SERVER-ICS",
        summary = "Daily 10-11",
        rrule = "FREQ=DAILY;COUNT=5",
    ).copy(url = eventId.url, etag = "2")

    private fun target(scope: RecurrenceScope, day: Int = 16) = OccurrenceTarget.Recurring(
        occurrenceOf(eventId, LocalDateTime(2026, 6, day, 10, 0)),
        scope,
    )

    private suspend fun renameMaster() = repository.updateEvent(
        sourceCredentials = CREDENTIALS,
        destinationCredentials = CREDENTIALS,
        eventId = eventId,
        data = checkNotNull(repository.getEditData(OccurrenceId.Master(eventId))).copy(title = "Renamed"),
    )

    @Test
    fun update_keepsOriginalEtagDuringSyncAndRebuildsFromServerIcs() = runTest {
        seedSeries()
        val remote = serverSeries().let { it.copy(content = it.content.copy(description = "Remote description")) }
        fakeCaldav.storedEvents = listOf(remote)
        fakeCaldav.updateFailures += conflict()
        fakeCaldav.updatedEtag = "3"
        fakeCaldav.patchFromIcs = { ics, edit ->
            if (ics == "LOCAL-ICS") {
                // A sync commits after the initial snapshot, but before the first PUT.
                val local = checkNotNull(eventDao().getEvent(eventId))
                eventDao().upsert(listOf(EventWithRawIcs(local.copy(etag = "2"), "SERVER-ICS")))
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

        renameMaster()

        assertEquals(listOf("1" to "LOCAL-ICS-PATCHED", "2" to "SERVER-ICS-PATCHED"), fakeCaldav.updates)
        assertEquals(listOf("LOCAL-ICS", "SERVER-ICS"), fakeCaldav.patchInputs)
        assertEquals("SERVER-ICS-PATCHED", eventDao().getRawIcs(eventId))
        val stored = assertNotNull(eventDao().getEvent(eventId))
        assertEquals("3", stored.etag)
        assertEquals("Renamed", stored.content.summary)
        assertEquals("Remote description", stored.content.description)
    }

    @Test
    fun update_secondConflictIsNotRetried() = runTest {
        seedSeries()
        val second = conflict()
        fakeCaldav.storedEvents = listOf(serverSeries())
        fakeCaldav.updateFailures += conflict()
        fakeCaldav.updateFailures += second

        assertSame(second, assertFailsWith<CaldavHttpException> { renameMaster() })

        assertEquals(2, fakeCaldav.updates.size)
        assertEquals(1, fakeCaldav.fetches.size)
        assertEquals("SERVER-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun update_missingRemoteIsStillAConflict() = runTest {
        seedSeries()
        val failure = conflict()
        fakeCaldav.updateFailures += failure

        assertSame(failure, assertFailsWith<CaldavHttpException> { renameMaster() })

        assertEquals(1, fakeCaldav.updates.size)
        assertNotNull(eventDao().getEvent(eventId))
    }

    @Test
    fun delete_missingRemoteIsSuccess() = runTest {
        seedSeries()
        fakeCaldav.deleteFailures += conflict("Delete")

        repository.deleteEvent(CREDENTIALS, eventId)

        assertEquals(listOf(eventId.url to "1"), fakeCaldav.deletes)
        assertEquals(1, fakeCaldav.fetches.size)
        assertNull(eventDao().getEvent(eventId))
    }

    @Test
    fun delete_failedFetchDoesNotMeanMissing() = runTest {
        seedSeries()
        val failure = conflict("Delete")
        fakeCaldav.deleteFailures += failure
        fakeCaldav.fetchFailure = IllegalStateException("Network unavailable")

        assertSame(failure, assertFailsWith<CaldavHttpException> { repository.deleteEvent(CREDENTIALS, eventId) })

        assertEquals(1, fakeCaldav.deletes.size)
        assertNotNull(eventDao().getEvent(eventId))
    }

    @Test
    fun delete_occurrenceRetryPreservesConcurrentExdateAndOverride() = runTest {
        seedSeries()
        val remote = serverSeries().let { series ->
            series.copy(
                exDates = listOf(RemoteIcalDateValue(value = "20260617T100000Z", valueType = RemoteIcalDateValueType.DateTime, tzid = null)),
                overrides = listOf(remoteOverride(series, 18)),
            )
        }
        fakeCaldav.storedEvents = listOf(remote)
        fakeCaldav.updateFailures += conflict()
        fakeCaldav.patchFromIcs = { ics, edit ->
            val exDates = assertIs<RemoteDateListChange.Set>(edit.exDateChange).lines.flatMap { it.values }
            val base = if (ics == "LOCAL-ICS") serverSeries() else remote
            if (ics == "SERVER-ICS") assertEquals(setOf("20260616T100000Z", "20260617T100000Z"), exDates.toSet())
            base.copy(
                icsData = "$ics-PATCHED",
                exDates = exDates.map { RemoteIcalDateValue(value = it, valueType = RemoteIcalDateValueType.DateTime, tzid = null) },
            )
        }

        repository.deleteEvent(CREDENTIALS, target(RecurrenceScope.ThisOccurrence))

        assertEquals(listOf("1" to "LOCAL-ICS-PATCHED", "2" to "SERVER-ICS-PATCHED"), fakeCaldav.updates)
        assertEquals(setOf(icalUtc(2026, 6, 16), icalUtc(2026, 6, 17)), eventDao().getEvent(eventId)!!.exDates.toSet())
        assertEquals("20260618T100000Z", eventDao().getOverridesOf(eventId).single().recurrenceIdValue)
        assertEquals("SERVER-ICS-PATCHED", eventDao().getRawIcs(eventId))
    }

    @Test
    fun delete_alreadyExcludedOccurrenceNeedsNoSecondPut() = runTest {
        seedSeries()
        fakeCaldav.storedEvents = listOf(serverSeries().copy(
            exDates = listOf(RemoteIcalDateValue(value = "20260616T100000Z", valueType = RemoteIcalDateValueType.DateTime, tzid = null)),
        ))
        fakeCaldav.updateFailures += conflict()

        repository.deleteEvent(CREDENTIALS, target(RecurrenceScope.ThisOccurrence))

        assertEquals(1, fakeCaldav.updates.size)
        assertEquals("SERVER-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun delete_followingRetryRecomputesTheRule() = runTest {
        seedSeries()
        fakeCaldav.storedEvents = listOf(serverSeries().copy(rrule = "FREQ=DAILY;COUNT=8"))
        fakeCaldav.updateFailures += conflict()
        fakeCaldav.patchFromIcs = { ics, edit ->
            val rule = assertIs<RemoteRecurrenceChange.Set>(edit.recurrenceChange).value
            serverSeries().copy(icsData = "$ics-PATCHED", rrule = rule)
        }

        repository.deleteEvent(CREDENTIALS, target(RecurrenceScope.ThisAndFollowing, day = 18))

        assertEquals(listOf("LOCAL-ICS", "SERVER-ICS"), fakeCaldav.patchInputs)
        assertEquals("FREQ=DAILY;COUNT=3", assertIs<RemoteRecurrenceChange.Set>(fakeCaldav.patches.last().recurrenceChange).value)
        assertEquals("SERVER-ICS-PATCHED", eventDao().getRawIcs(eventId))
    }

    @Test
    fun update_occurrenceRetryUsesNewOverrideAndPreservesItsDescription() = runTest {
        seedSeries()
        val remote = serverSeries().let { it.copy(overrides = listOf(remoteOverride(it, 16), remoteOverride(it, 18))) }
        fakeCaldav.storedEvents = listOf(remote)
        fakeCaldav.updateFailures += conflict()
        fakeCaldav.overrideFromIcs = { ics, id, edit ->
            val base = if (ics == "LOCAL-ICS") serverSeries() else remote
            if (ics == "SERVER-ICS") assertEquals("Remote override 16", edit.description)
            val changed = remoteOverride(base, 16).let { it.copy(content = it.content.copy(summary = edit.summary, description = edit.description)) }
            base.copy(icsData = "$ics-PATCHED", overrides = base.overrides.filterNot { it.recurrenceId == id.value } + changed)
        }
        val occurrence = occurrenceOf(eventId, LocalDateTime(2026, 6, 16, 10, 0))
        val edit = checkNotNull(repository.getEditData(occurrence)).copy(title = "Renamed")

        repository.updateEvent(CREDENTIALS, CREDENTIALS, target(RecurrenceScope.ThisOccurrence), edit)

        assertEquals(listOf("LOCAL-ICS", "SERVER-ICS"), fakeCaldav.overrideInputs)
        assertEquals(setOf("20260616T100000Z", "20260618T100000Z"), eventDao().getOverridesOf(eventId).map { it.recurrenceIdValue }.toSet())
        assertEquals("SERVER-ICS-PATCHED", eventDao().getRawIcs(eventId))
    }

    @Test
    fun update_seriesRetryPropagatesOriginalChangeToNewOverride() = runTest {
        seedSeries()
        val remote = serverSeries().let { it.copy(overrides = listOf(remoteOverride(it, 18))) }
        fakeCaldav.storedEvents = listOf(remote)
        fakeCaldav.updateFailures += conflict()
        fakeCaldav.patchFromIcs = { ics, edit ->
            val base = if (ics == "LOCAL-ICS") serverSeries() else remote
            base.copy(icsData = "$ics-PATCHED", content = base.content.copy(summary = edit.summary))
        }
        fakeCaldav.overrideFromIcs = { ics, _, edit ->
            assertEquals("SERVER-ICS-PATCHED", ics)
            assertEquals("Remote override 18", edit.description)
            remote.copy(
                icsData = "$ics-OVERRIDE",
                content = remote.content.copy(summary = edit.summary),
                overrides = remote.overrides.map { it.copy(content = it.content.copy(summary = edit.summary)) },
            )
        }
        val occurrence = occurrenceOf(eventId, LocalDateTime(2026, 6, 16, 10, 0))
        val edit = checkNotNull(repository.getEditData(occurrence)).copy(title = "Renamed")

        repository.updateEvent(CREDENTIALS, CREDENTIALS, target(RecurrenceScope.AllOccurrences), edit)

        assertEquals(listOf("LOCAL-ICS", "SERVER-ICS"), fakeCaldav.patchInputs)
        assertEquals("Renamed", fakeCaldav.overrideUpserts.single().second.summary)
        assertEquals("SERVER-ICS-PATCHED-OVERRIDE", eventDao().getRawIcs(eventId))
        assertEquals("Renamed", eventDao().getOverridesOf(eventId).single().content.summary)
        assertEquals("Renamed", eventDao().getEvent(eventId)!!.content.summary)
    }

    @Test
    fun delete_followingWithMissingPivotDoesNotSilentlySucceed() = runTest {
        seedSeries()
        val failure = conflict()
        fakeCaldav.updateFailures += failure
        fakeCaldav.storedEvents = listOf(serverSeries().copy(
            exDates = listOf(RemoteIcalDateValue(value = "20260616T100000Z", valueType = RemoteIcalDateValueType.DateTime, tzid = null)),
        ))

        assertSame(failure, assertFailsWith<CaldavHttpException> {
            repository.deleteEvent(CREDENTIALS, target(RecurrenceScope.ThisAndFollowing))
        })

        assertEquals(1, fakeCaldav.updates.size)
        assertEquals("SERVER-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun split_conflictDoesNotRecreateTailOrTruncateRefreshedHead() = runTest {
        seedSeries()
        val failure = conflict()
        val tailId = EventId("https://cal/main/tail.ics")
        fakeCaldav.createdRef = RemoteDavEventRef(url = tailId.url, etag = "tail")
        fakeCaldav.storedEvents = listOf(serverSeries())
        fakeCaldav.updateFailures += failure
        val occurrence = occurrenceOf(eventId, LocalDateTime(2026, 6, 16, 10, 0))
        val edit = checkNotNull(repository.getEditData(occurrence)).copy(title = "Tail")

        assertSame(failure, assertFailsWith<CaldavHttpException> {
            repository.updateEvent(CREDENTIALS, CREDENTIALS, target(RecurrenceScope.ThisAndFollowing), edit)
        })

        assertEquals(1, fakeCaldav.creates.size)
        assertEquals(listOf("1"), fakeCaldav.updates.map { it.first })
        assertEquals(0, fakeCaldav.fetches.size)
        assertNotNull(eventDao().getEvent(tailId))
        assertEquals("LOCAL-ICS", eventDao().getRawIcs(eventId))
    }

    @Test
    fun move_conflictDoesNotDeleteANewerSourceOrRecreateDestination() = runTest {
        seedSeries()
        val destination = CalendarId("calendar://other")
        seedCalendar(calendarId = destination)
        val movedId = EventId("https://cal/other/series.ics")
        fakeCaldav.createdRef = RemoteDavEventRef(url = movedId.url, etag = "destination")
        fakeCaldav.storedEvents = listOf(serverSeries())
        val failure = conflict("Delete")
        fakeCaldav.deleteFailures += failure
        val edit = checkNotNull(repository.getEditData(OccurrenceId.Master(eventId))).copy(calendarId = destination)

        assertSame(failure, assertFailsWith<CaldavHttpException> {
            repository.updateEvent(CREDENTIALS, CREDENTIALS, eventId, edit)
        })

        assertEquals(1, fakeCaldav.creates.size)
        assertEquals(listOf(eventId.url to "1"), fakeCaldav.deletes)
        assertEquals(0, fakeCaldav.fetches.size)
        assertNotNull(eventDao().getEvent(eventId))
        assertEquals(destination, eventDao().getEvent(movedId)!!.calendarId)
    }

    private fun remoteOverride(series: RemoteDavEvent, day: Int): RemoteDavEventOverride {
        val date = "202606$day"
        return RemoteDavEventOverride(
            recurrenceId = "${date}T100000Z",
            recurrenceIdTzid = null,
            recurrenceIdValueType = RemoteIcalDateValueType.DateTime,
            isThisAndFuture = false,
            content = series.content.copy(
                summary = "Override $day",
                description = "Remote override $day",
                dtstart = "${date}T100000Z",
                dtend = "${date}T110000Z",
            ),
        )
    }
}
