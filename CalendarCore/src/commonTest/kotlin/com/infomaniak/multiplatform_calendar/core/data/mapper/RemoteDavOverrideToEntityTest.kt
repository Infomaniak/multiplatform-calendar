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

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventOverride
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteIcalDateValueType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteDavOverrideToEntityTest {

    @Test
    fun overrideIsKeyedOnItsRecurrenceIdNotOnItsMovedStart() {
        val event = remoteEvent(
            dtstart = "20260615T100000",
            dtStartTzid = "Europe/Zurich",
            overrides = listOf(
                override(recurrenceId = "20260617T100000", recurrenceIdTzid = "Europe/Zurich", dtstart = "20260617T140000"),
            ),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(EventId(event.url), override.masterId)
        assertEquals(RecurrenceKey.Zoned(LocalDateTime(2026, 6, 17, 10, 0), "Europe/Zurich"), override.recurrenceKey)
        assertEquals(LocalDateTime(2026, 6, 17, 14, 0), override.content.timing.dtStart)
    }

    @Test
    fun originalBoundsSpanTheMastersDuration() {
        val event = remoteEvent(
            dtstart = "20260615T100000Z",
            dtend = "20260615T113000Z",
            overrides = listOf(override(recurrenceId = "20260617T100000Z", dtstart = "20260617T140000Z")),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(LocalDateTime(2026, 6, 17, 10, 0), override.originalStartLocalDateTime)
        assertEquals(LocalDateTime(2026, 6, 17, 11, 30), override.originalEndLocalDateTime)
        assertEquals(LocalDateTime(2026, 6, 17, 10, 0).toEpochMs(TimeZone.UTC), override.originalStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 17, 11, 30).toEpochMs(TimeZone.UTC), override.originalEndInstantMs)
    }

    @Test
    fun floatingOverrideHasNoAbsoluteOriginalBounds() {
        val event = remoteEvent(
            dtstart = "20260615T100000",
            overrides = listOf(override(recurrenceId = "20260617T100000", dtstart = "20260617T140000")),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(RecurrenceKey.Floating(LocalDateTime(2026, 6, 17, 10, 0)), override.recurrenceKey)
        assertNull(override.originalStartInstantMs)
        assertNull(override.originalEndInstantMs)
    }

    @Test
    fun allDayOverrideIsKeyedOnItsDate() {
        val event = remoteEvent(
            dtstart = "20260615",
            overrides = listOf(
                override(
                    recurrenceId = "20260617",
                    recurrenceIdValueType = RemoteIcalDateValueType.Date,
                    dtstart = "20260619",
                ),
            ),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(RecurrenceKey.AllDay(LocalDate(2026, 6, 17)), override.recurrenceKey)
        assertTrue(override.content.timing.isAllDay)
    }

    @Test
    fun cancelledOverrideKeepsItsStatusRatherThanBecomingAnExDate() {
        val event = remoteEvent(
            dtstart = "20260615T100000Z",
            overrides = listOf(override(recurrenceId = "20260617T100000Z", dtstart = "20260617T100000Z", status = "CANCELLED")),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(EventStatus.CANCELLED, override.content.status)
        assertTrue(event.exDates.isEmpty())
    }

    @Test
    fun overrideWhoseFormDiffersFromTheMasterIsSkippedWithoutLosingItsSiblings() {
        val event = remoteEvent(
            dtstart = "20260615T100000Z",
            overrides = listOf(
                override(recurrenceId = "20260617", recurrenceIdValueType = RemoteIcalDateValueType.Date, dtstart = "20260619"),
                override(recurrenceId = "20260618T100000Z", dtstart = "20260618T140000Z"),
            ),
        )

        val overrides = event.toOverrideEntities(event.toTimingEntity())

        assertEquals(1, overrides.size)
        assertEquals(RecurrenceKey.Utc("2026-06-18T10:00:00Z".toInstantValue()), overrides.single().recurrenceKey)
    }

    @Test
    fun utcRecurrenceIdIsNormalisedIntoTheZonedMastersOwnForm() {
        val event = remoteEvent(
            dtstart = "20260615T100000",
            dtStartTzid = "Europe/Zurich",
            overrides = listOf(override(recurrenceId = "20260617T080000Z", dtstart = "20260617T140000Z")),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        // 08:00Z is 10:00 in Europe/Zurich in June: the key must be the one the expander will emit.
        assertEquals(RecurrenceKey.Zoned(LocalDateTime(2026, 6, 17, 10, 0), "Europe/Zurich"), override.recurrenceKey)
        assertEquals(LocalDateTime(2026, 6, 17, 10, 0), override.originalStartLocalDateTime)
    }

    @Test
    fun zonedRecurrenceIdIsNormalisedIntoTheUtcMastersOwnForm() {
        val event = remoteEvent(
            dtstart = "20260615T080000Z",
            overrides = listOf(
                override(recurrenceId = "20260617T100000", recurrenceIdTzid = "Europe/Zurich", dtstart = "20260617T140000Z"),
            ),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(RecurrenceKey.Utc("2026-06-17T08:00:00Z".toInstantValue()), override.recurrenceKey)
    }

    @Test
    fun bareRecurrenceIdOnAnAnchoredMasterInheritsItsZone() {
        val event = remoteEvent(
            dtstart = "20260615T100000",
            dtStartTzid = "Europe/Zurich",
            overrides = listOf(override(recurrenceId = "20260617T100000", dtstart = "20260617T140000")),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(RecurrenceKey.Zoned(LocalDateTime(2026, 6, 17, 10, 0), "Europe/Zurich"), override.recurrenceKey)
        assertEquals(
            LocalDateTime(2026, 6, 17, 10, 0).toEpochMs(TimeZone.of("Europe/Zurich")),
            override.originalStartInstantMs,
        )
    }

    @Test
    fun zonedRecurrenceIdCannotIdentifyAnInstanceOfAFloatingMaster() {
        val event = remoteEvent(
            dtstart = "20260615T100000",
            overrides = listOf(
                override(recurrenceId = "20260617T100000", recurrenceIdTzid = "Europe/Zurich", dtstart = "20260617T140000"),
            ),
        )

        assertTrue(event.toOverrideEntities(event.toTimingEntity()).isEmpty())
    }

    @Test
    fun utcRecurrenceIdCannotIdentifyAnInstanceOfAFloatingMaster() {
        val event = remoteEvent(
            dtstart = "20260615T100000",
            overrides = listOf(override(recurrenceId = "20260617T080000Z", dtstart = "20260617T140000")),
        )

        assertTrue(event.toOverrideEntities(event.toTimingEntity()).isEmpty())
    }

    @Test
    fun originalEndFollowsTheMastersOwnDtEndZone() {
        val event = remoteEvent(
            dtstart = "20260615T090000",
            dtStartTzid = "America/New_York",
            dtend = "20260615T160000",
            dtEndTzid = "Europe/Paris",
            overrides = listOf(
                override(recurrenceId = "20260617T090000", recurrenceIdTzid = "America/New_York", dtstart = "20260617T100000"),
            ),
        )

        val override = event.toOverrideEntities(event.toTimingEntity()).single()

        assertEquals(LocalDateTime(2026, 6, 17, 16, 0), override.originalEndLocalDateTime)
    }

    @Test
    fun thisAndFutureOnANonRecurringEventIsNotReportedAsADroppedRecurrence() {
        val event = remoteEvent(
            dtstart = "20260615T100000Z",
            overrides = listOf(override(recurrenceId = "20260617T100000Z", dtstart = "20260617T140000Z", isThisAndFuture = true)),
        )

        assertTrue(event.resolveRecurrenceSet().rDates.isEmpty())
    }

    @Test
    fun thisAndFutureOverrideIsKeptAsAPlainSingleInstanceOverride() {
        val event = remoteEvent(
            dtstart = "20260615T100000Z",
            rrule = "FREQ=DAILY;COUNT=5",
            overrides = listOf(override(recurrenceId = "20260617T100000Z", dtstart = "20260617T140000Z", isThisAndFuture = true)),
        )

        val recurrence = event.resolveRecurrenceSet()

        assertNotNull(recurrence.rule)
        val override = event.toOverrideEntities(event.toTimingEntity()).single()
        assertEquals(LocalDateTime(2026, 6, 17, 14, 0), override.content.timing.dtStart)
    }

    @Test
    fun editingASeriesKeepsTheOverridesCarriedByThePatchedIcs() {
        val event = remoteEvent(
            dtstart = "20260615T100000Z",
            rrule = "FREQ=DAILY;COUNT=5",
            overrides = listOf(override(recurrenceId = "20260617T100000Z", dtstart = "20260617T140000Z")),
        )

        val upsert = event.toSyncedUpsert(
            ref = RemoteDavEventRef(url = event.url, etag = "etag-2"),
            calendarId = CalendarId("https://cal/tests/"),
        )

        assertEquals(
            RecurrenceKey.Utc("2026-06-17T10:00:00Z".toInstantValue()),
            upsert.overrides.single().recurrenceKey,
        )
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun LocalDateTime.toEpochMs(zone: TimeZone): Long = toInstant(zone).toEpochMilliseconds()

    private fun String.toInstantValue() = kotlin.time.Instant.parse(this)

    private fun override(
        recurrenceId: String,
        dtstart: String,
        recurrenceIdTzid: String? = null,
        recurrenceIdValueType: RemoteIcalDateValueType = RemoteIcalDateValueType.DateTime,
        isThisAndFuture: Boolean = false,
        status: String? = null,
    ) = RemoteDavEventOverride(
        recurrenceId = recurrenceId,
        recurrenceIdTzid = recurrenceIdTzid,
        recurrenceIdValueType = recurrenceIdValueType,
        isThisAndFuture = isThisAndFuture,
        summary = "Moved",
        description = null,
        location = null,
        dtstart = dtstart,
        dtStartTzid = recurrenceIdTzid,
        dtend = null,
        dtEndTzid = null,
        duration = null,
        created = null,
        lastModified = null,
        dtstamp = null,
        status = status,
        transp = null,
        classification = null,
        priority = null,
        sequence = null,
        categories = null,
        colorHex = null,
        colorIcalName = null,
    )

    private fun remoteEvent(
        dtstart: String,
        dtStartTzid: String? = null,
        dtend: String? = null,
        dtEndTzid: String? = null,
        rrule: String? = null,
        overrides: List<RemoteDavEventOverride> = emptyList(),
    ) = RemoteDavEvent(
        url = "https://cal/tests/series.ics",
        etag = "etag-1",
        icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
        uid = "uid-1",
        summary = "Master",
        description = null,
        location = null,
        dtstart = dtstart,
        dtStartTzid = dtStartTzid,
        dtend = dtend,
        dtEndTzid = dtEndTzid,
        duration = null,
        created = null,
        lastModified = null,
        dtstamp = null,
        rrule = rrule,
        status = null,
        transp = null,
        classification = null,
        priority = null,
        sequence = null,
        categories = null,
        colorHex = null,
        colorIcalName = null,
        attendees = emptyList(),
        overrides = overrides,
    )
}
