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

import com.infomaniak.multiplatform_calendar.core.data.exception.CaldavParsingException
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RemoteDavEventToTimingEntityTest {
    private val calendarId = CalendarId("calendar://tests")
    private val paris = TimeZone.of("Europe/Paris")
    private val newYork = TimeZone.of("America/New_York")

    // ---- All-day (VALUE=DATE) -------------------------------------------------------------------

    @Test
    fun allDay_storesBothZonesAsNull_andEpochAnchoredInUtc() {
        val timing = remoteEvent(
            dtstart = "20260615", // VALUE=DATE
            dtend = "20260616",
        ).toTimingEntity()

        assertEquals(true, timing.isAllDay)
        assertNull(timing.startTimeZone)
        assertNull(timing.endTimeZone)
        assertEquals(LocalDateTime(2026, 6, 15, 0, 0).toEpochMs(TimeZone.UTC), timing.dtStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 16, 0, 0).toEpochMs(TimeZone.UTC), timing.dtEndInstantMs)
    }

    // ---- UTC (Z suffix) -------------------------------------------------------------------------

    @Test
    fun utcEvent_storesUtcZone_andAnchoredInstants() {
        val timing = remoteEvent(
            dtstart = "20260615T100000Z",
            dtend = "20260615T110000Z",
        ).toTimingEntity()

        assertEquals(false, timing.isAllDay)
        assertEquals("UTC", timing.startTimeZone)
        assertEquals("UTC", timing.endTimeZone)
        assertEquals(LocalDateTime(2026, 6, 15, 10, 0).toEpochMs(TimeZone.UTC), timing.dtStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 15, 11, 0).toEpochMs(TimeZone.UTC), timing.dtEndInstantMs)
    }

    // ---- Zoned (TZID) ---------------------------------------------------------------------------

    @Test
    fun zonedEvent_storesTzid_andAnchoredInstantsInThatZone() {
        val timing = remoteEvent(
            dtstart = "20260615T140000",
            dtStartTzid = "Europe/Paris",
            dtend = "20260615T150000",
            dtEndTzid = "Europe/Paris",
        ).toTimingEntity()

        assertEquals("Europe/Paris", timing.startTimeZone)
        assertEquals("Europe/Paris", timing.endTimeZone)
        assertEquals(LocalDateTime(2026, 6, 15, 14, 0).toEpochMs(paris), timing.dtStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 15, 15, 0).toEpochMs(paris), timing.dtEndInstantMs)
    }

    // ---- Cross-zone (RFC 5545 §3.8.2.2) ---------------------------------------------------------

    @Test
    fun flightEvent_keepsBothZonesIndependent() {
        val timing = remoteEvent(
            dtstart = "20260615T090000",
            dtStartTzid = "America/New_York",
            dtend = "20260615T210000",
            dtEndTzid = "Europe/Paris",
        ).toTimingEntity()

        assertEquals("America/New_York", timing.startTimeZone)
        assertEquals("Europe/Paris", timing.endTimeZone)
        assertEquals(LocalDateTime(2026, 6, 15, 9, 0).toEpochMs(newYork), timing.dtStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 15, 21, 0).toEpochMs(paris), timing.dtEndInstantMs)
    }

    @Test
    fun zonedEvent_dtendWithoutOwnTzid_inheritsStartZone() {
        // RFC 5545: a bare local DATE-TIME on DTEND inherits DTSTART's anchor unless flagged UTC.
        val timing = remoteEvent(
            dtstart = "20260615T140000",
            dtStartTzid = "Europe/Paris",
            dtend = "20260615T150000",
            dtEndTzid = null,
        ).toTimingEntity()

        assertEquals("Europe/Paris", timing.startTimeZone)
        assertEquals("Europe/Paris", timing.endTimeZone)
    }

    // ---- Floating (FORM #1: no TZID, no Z) ------------------------------------------------------

    @Test
    fun floatingEvent_storesBothZonesAndInstantsAsNull() {
        val timing = remoteEvent(
            dtstart = "20260615T100000",
            dtend = "20260615T110000",
        ).toTimingEntity()

        assertNull(timing.startTimeZone)
        assertNull(timing.endTimeZone)
        // Per RFC 5545 FORM #1 a floating event has no absolute instant — DAO uses wall-clock branch.
        assertNull(timing.dtStartInstantMs)
        assertNull(timing.dtEndInstantMs)
    }

    // ---- DURATION -------------------------------------------------------------------------------

    @Test
    fun durationEvent_computesDtEndEffective_fromStartPlusDuration() {
        val timing = remoteEvent(
            dtstart = "20260615T100000Z",
            dtend = null,
            duration = "PT2H",
        ).toTimingEntity()

        assertEquals(LocalDateTime(2026, 6, 15, 12, 0), timing.dtEndEffective)
        assertEquals(2.hours, timing.duration)
        // Anchored via the end zone (== start zone for a single-zoned event).
        assertEquals(LocalDateTime(2026, 6, 15, 12, 0).toEpochMs(TimeZone.UTC), timing.dtEndInstantMs)
    }

    @Test
    fun allDayEvent_withNoDtEnd_defaultsToPlusOneDay() {
        // RFC 5545 §3.6.1: an all-day event without DTEND spans one day.
        val timing = remoteEvent(
            dtstart = "20260615",
            dtend = null,
        ).toTimingEntity()

        assertEquals(LocalDateTime(2026, 6, 16, 0, 0), timing.dtEndEffective)
    }

    // ---- Windows TZIDs (Outlook / Exchange / M365) ---------------------------------------------

    @Test
    fun windowsTzid_dtstartAndDtendCanUseDifferentWindowsZones() {
        val timing = remoteEvent(
            dtstart = "20260615T090000",
            dtStartTzid = "Eastern Standard Time",
            dtend = "20260615T210000",
            dtEndTzid = "W. Europe Standard Time",
        ).toTimingEntity()

        assertEquals("America/New_York", timing.startTimeZone)
        assertEquals("Europe/Berlin", timing.endTimeZone)
    }

    // ---- Errors ---------------------------------------------------------------------------------

    @Test
    fun missingDtstart_throwsCaldavParsingException() {
        val remote = remoteEvent(dtstart = null)
        assertFailsWith<CaldavParsingException> { remote.toTimingEntity() }
    }

    // ---- Classification & categories ------------------------------------------------------------

    @Test
    fun classification_standardValue_isParsedCaseInsensitively() {
        val entity = remoteEvent(dtstart = "20260615T100000Z", classification = "private").toEntity(calendarId)

        assertEquals(Classification.Private, entity.classification)
    }

    @Test
    fun classification_customValue_isKeptVerbatim() {
        val entity = remoteEvent(dtstart = "20260615T100000Z", classification = "X-CUSTOM").toEntity(calendarId)

        assertEquals(Classification.Custom("X-CUSTOM"), entity.classification)
    }

    @Test
    fun classification_absent_isNull() {
        val entity = remoteEvent(dtstart = "20260615T100000Z", classification = null).toEntity(calendarId)

        assertNull(entity.classification)
    }

    @Test
    fun categories_areSplitTrimmedAndBlanksDropped() {
        val entity = remoteEvent(dtstart = "20260615T100000Z", categories = " work , ,personal ").toEntity(calendarId)

        assertEquals(listOf("work", "personal"), entity.categories)
    }

    @Test
    fun categories_preserveEscapedTextChars() {
        // RFC 5545 §3.3.11: comma-separated TEXT values with \, \\ \; \n escapes.
        val entity = remoteEvent(
            dtstart = "20260615T100000Z",
            categories = """work\, ops,path\\to,semi\;colon,line\nbreak""",
        ).toEntity(calendarId)

        assertEquals(listOf("work, ops", """path\to""", "semi;colon", "line\nbreak"), entity.categories)
    }

    @Test
    fun categories_absentOrBlank_isNull() {
        assertNull(remoteEvent(dtstart = "20260615T100000Z", categories = null).toEntity(calendarId).categories)
        assertNull(remoteEvent(dtstart = "20260615T100000Z", categories = " , ").toEntity(calendarId).categories)
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun remoteEvent(
        dtstart: String?,
        dtStartTzid: String? = null,
        dtend: String? = null,
        dtEndTzid: String? = null,
        duration: String? = null,
        classification: String? = null,
        categories: String? = null,
    ) = RemoteDavEvent(
        url = "https://cal/tests/${dtstart ?: "empty"}.ics",
        etag = "etag-1",
        icsData = "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
        uid = "uid-1",
        summary = "Test",
        description = null,
        location = null,
        dtstart = dtstart,
        dtStartTzid = dtStartTzid,
        dtend = dtend,
        dtEndTzid = dtEndTzid,
        duration = duration,
        created = null,
        lastModified = null,
        dtstamp = null,
        rrule = null,
        status = null,
        transp = null,
        classification = classification,
        priority = null,
        sequence = null,
        categories = categories,
        attendees = emptyList(),
    )

    private fun LocalDateTime.toEpochMs(zone: TimeZone): Long = toInstant(zone).toEpochMilliseconds()
}
