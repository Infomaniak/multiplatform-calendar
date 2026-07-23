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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EventTimingMappersTest {

    private val paris = TimeZone.of("Europe/Paris")
    private val newYork = TimeZone.of("America/New_York")

    // ---- EventTiming.toEntity() -----------------------------------------------------------------

    @Test
    fun toEntity_allDay_anchorsEpochInUtcRegardlessOfDeviceZone() {
        val entity = timing(
            start = LocalDateTime(2026, 6, 15, 0, 0),
            end = LocalDateTime(2026, 6, 16, 0, 0),
            startTimeZone = null,
            endTimeZone = null,
            isAllDay = true,
        ).toEntity()

        assertEquals(LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds(), entity.dtStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 16, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds(), entity.dtEndInstantMs)
        assertNull(entity.startTimeZone)
        assertNull(entity.endTimeZone)
        assertEquals(true, entity.isAllDay)
    }

    @Test
    fun toEntity_zoned_anchorsEpochInDeclaredZone() {
        val entity = timing(
            start = LocalDateTime(2026, 6, 15, 14, 0),
            end = LocalDateTime(2026, 6, 15, 15, 0),
            startTimeZone = paris,
            endTimeZone = paris,
            isAllDay = false,
        ).toEntity()

        assertEquals("Europe/Paris", entity.startTimeZone)
        assertEquals("Europe/Paris", entity.endTimeZone)
        assertEquals(LocalDateTime(2026, 6, 15, 14, 0).toInstant(paris).toEpochMilliseconds(), entity.dtStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 15, 15, 0).toInstant(paris).toEpochMilliseconds(), entity.dtEndInstantMs)
    }

    @Test
    fun toEntity_floating_hasNullEpochAndNullZones() {
        // RFC 5545 FORM #1: no TZID, no Z. No absolute instant exists at insertion time.
        val entity = timing(
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 11, 0),
            startTimeZone = null,
            endTimeZone = null,
            isAllDay = false,
        ).toEntity()

        assertNull(entity.dtStartInstantMs)
        assertNull(entity.dtEndInstantMs)
        assertNull(entity.startTimeZone)
        assertNull(entity.endTimeZone)
    }

    @Test
    fun toEntity_crossZoneFlight_resolvesEachEpochInItsOwnZone() {
        // RFC 5545 §3.8.2.2 lets DTSTART and DTEND declare different TZIDs (e.g. flights).
        val entity = timing(
            start = LocalDateTime(2026, 6, 15, 9, 0),
            end = LocalDateTime(2026, 6, 15, 21, 0),
            startTimeZone = newYork,
            endTimeZone = paris,
            isAllDay = false,
        ).toEntity()

        assertEquals("America/New_York", entity.startTimeZone)
        assertEquals("Europe/Paris", entity.endTimeZone)
        assertEquals(LocalDateTime(2026, 6, 15, 9, 0).toInstant(newYork).toEpochMilliseconds(), entity.dtStartInstantMs)
        assertEquals(LocalDateTime(2026, 6, 15, 21, 0).toInstant(paris).toEpochMilliseconds(), entity.dtEndInstantMs)
    }

    @Test
    fun toEntity_dropsDuration_andCopiesEndIntoDtEndEffective() {
        // RFC 5545 §3.8.2.5: DTEND and DURATION are mutually exclusive; the edited timing always
        // carries an explicit end, so any pre-existing DURATION must be dropped.
        val entity = timing(
            start = LocalDateTime(2026, 6, 15, 10, 0),
            end = LocalDateTime(2026, 6, 15, 12, 30),
            startTimeZone = paris,
            endTimeZone = paris,
            isAllDay = false,
        ).toEntity()

        assertNull(entity.duration)
        assertEquals(LocalDateTime(2026, 6, 15, 12, 30), entity.dtEndEffective)
        assertEquals(LocalDateTime(2026, 6, 15, 12, 30), entity.dtEnd)
    }

    // ---- EventTimingEntity.toDomain() -----------------------------------------------------------

    @Test
    fun toDomain_zoned_parsesTimeZoneStringsAndKeepsWallClock() {
        val domain = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 15, 14, 0),
            dtEnd = LocalDateTime(2026, 6, 15, 15, 0),
            duration = null,
            dtEndEffective = LocalDateTime(2026, 6, 15, 15, 0),
            startTimeZone = "Europe/Paris",
            endTimeZone = "Europe/Paris",
            dtStartInstantMs = 0L,
            dtEndInstantMs = 0L,
            isAllDay = false,
        ).toDomain()

        assertEquals(paris, domain.startTimeZone)
        assertEquals(paris, domain.endTimeZone)
        assertEquals(LocalDateTime(2026, 6, 15, 14, 0), domain.start)
        assertEquals(LocalDateTime(2026, 6, 15, 15, 0), domain.end)
        assertEquals(false, domain.isAllDay)
    }

    @Test
    fun toDomain_allDay_zonesAreNull_andIsAllDayPreserved() {
        val domain = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 15, 0, 0),
            dtEnd = LocalDateTime(2026, 6, 16, 0, 0),
            duration = null,
            dtEndEffective = LocalDateTime(2026, 6, 16, 0, 0),
            startTimeZone = null,
            endTimeZone = null,
            dtStartInstantMs = 0L,
            dtEndInstantMs = 0L,
            isAllDay = true,
        ).toDomain()

        assertNull(domain.startTimeZone)
        assertNull(domain.endTimeZone)
        assertEquals(true, domain.isAllDay)
    }

    @Test
    fun toDomain_dtEndEffective_isMappedToEnd_notDtEnd() {
        // The persistence layer resolves DURATION / all-day-default into `dtEndEffective`; the domain
        // side must consume that resolved value (not the raw `dtEnd`).
        val domain = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            dtEnd = null,
            duration = null,
            dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
            startTimeZone = "Europe/Paris",
            endTimeZone = "Europe/Paris",
            dtStartInstantMs = 0L,
            dtEndInstantMs = 0L,
            isAllDay = false,
        ).toDomain()

        assertEquals(LocalDateTime(2026, 6, 15, 11, 0), domain.end)
    }

    @Test
    fun toDomain_propagatesRecurrenceRule() {
        val rule = RecurrenceRule(freq = Frequency.Daily)
        val domain = EventTimingEntity(
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            dtEnd = LocalDateTime(2026, 6, 15, 11, 0),
            duration = null,
            dtEndEffective = LocalDateTime(2026, 6, 15, 11, 0),
            startTimeZone = null,
            endTimeZone = null,
            dtStartInstantMs = 0L,
            dtEndInstantMs = 0L,
            isAllDay = false,
        ).toDomain(recurrenceRule = rule)

        assertEquals(rule, domain.recurrenceRule)
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private fun timing(
        start: LocalDateTime,
        end: LocalDateTime,
        startTimeZone: TimeZone?,
        endTimeZone: TimeZone?,
        isAllDay: Boolean,
    ) = EventTiming(
        start = start,
        end = end,
        startTimeZone = startTimeZone,
        endTimeZone = endTimeZone,
        isAllDay = isAllDay,
    )
}
