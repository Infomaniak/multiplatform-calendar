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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.dataset.EventRepositoryColorByDayDataset
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSourceColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

internal class EventRepositoryDotColorsTest : EventRepositoryTestBase() {

    @Test
    fun observeVisibleDotColorsByDay_groupsByDay_andDeduplicatesPerCalendarAndColor() = runTest {
        val account = AccountId(1)
        val calendarA = CalendarId("calendar://a")
        val calendarB = CalendarId("calendar://b")
        val red = CalendarSourceColor(0xFFE53935.toInt())
        val blue = CalendarSourceColor(0xFF1E88E5.toInt())
        seedCalendar(account, calendarA, red)
        seedCalendar(account, calendarB, blue)

        val events = EventRepositoryColorByDayDataset.groupingScenario(calendarA, calendarB)
        eventDao().upsert(
            events.map { event ->
                EventWithRawIcs(
                    event = timedEvent(
                        id = event.id,
                        calendarId = event.calendarId,
                        start = event.start,
                        end = event.end,
                    ),
                    rawIcs = "",
                )
            },
        )

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 17, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        val day15 = LocalDateTime(2026, 6, 15, 0, 0).date
        val day16 = LocalDateTime(2026, 6, 16, 0, 0).date
        val day17 = LocalDateTime(2026, 6, 17, 0, 0).date

        assertEquals(setOf(day15, day16), colorsByDay.keys)
        assertEquals(
            setOf(red.argb, blue.argb),
            colorsByDay.getValue(day15).map { it.sourceColor }.toSet(),
            "day 15 must expose one dot per calendar+color pair of its events",
        )
        assertEquals(
            setOf(red.argb),
            colorsByDay.getValue(day16).map { it.sourceColor }.toSet(),
            "day 16 must only expose calendar A color",
        )
        assertNull(colorsByDay[day17], "days without events must be omitted")
    }

    @Test
    fun observeVisibleDotColorsByDay_dotsEventOwnColor_besideItsCalendarColoredSibling() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://mixed-colors")
        val calendarColor = CalendarSourceColor(0xFF1E88E5.toInt())
        val eventColor = 0xFFE53935.toInt()
        seedCalendar(account, calendarId, calendarColor)

        eventDao().upsert(
            listOf(
                EventWithRawIcs(
                    timedEvent(
                        id = EventId("event://inherits-calendar-color"),
                        calendarId = calendarId,
                        start = LocalDateTime(2026, 6, 15, 8, 0),
                        end = LocalDateTime(2026, 6, 15, 9, 0),
                    ),
                    "",
                ),
                EventWithRawIcs(
                    timedEvent(
                        id = EventId("event://declares-its-own-color"),
                        calendarId = calendarId,
                        start = LocalDateTime(2026, 6, 15, 10, 0),
                        end = LocalDateTime(2026, 6, 15, 11, 0),
                        colorArgb = eventColor,
                    ),
                    "",
                ),
            ),
        )

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 16, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            listOf(calendarColor.argb, eventColor),
            colorsByDay.getValue(LocalDateTime(2026, 6, 15, 0, 0).date).map { it.sourceColor },
            "an event redefining its color must get its own dot, next to its calendar-colored sibling",
        )
    }

    @Test
    fun observeVisibleDotColorsByDay_expandsRecurringMasterIntoOccurrenceDays() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://rrule")
        val green = CalendarSourceColor(0xFF43A047.toInt())
        seedCalendar(account, calendarId, green)

        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        val dtEnd = LocalDateTime(2026, 6, 15, 11, 0)
        val timing = EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = dtEnd,
            startTimeZone = TimeZone.UTC.id,
            endTimeZone = TimeZone.UTC.id,
            dtStartInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            dtEndInstantMs = dtEnd.toInstant(TimeZone.UTC).toEpochMilliseconds(),
        )
        val rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3)
        val master = EventEntity(
            id = EventId("event://colors-rrule"),
            calendarId = calendarId,
            content = EventContentEntity(
                summary = "Daily recurring",
                timing = timing,
            ),
            rrule = rrule,
            recurrenceBounds = checkNotNull(
                toRecurrenceBoundsEntity(
                    timing = timing,
                    recurrenceRule = rrule,
                    rDates = emptyList(),
                ),
            ),
            etag = "1",
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "")))

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        val expectedDays = setOf(
            LocalDateTime(2026, 6, 15, 0, 0).date,
            LocalDateTime(2026, 6, 16, 0, 0).date,
            LocalDateTime(2026, 6, 17, 0, 0).date,
        )
        assertEquals(expectedDays, colorsByDay.keys)
        expectedDays.forEach { day ->
            assertEquals(
                setOf(green.argb),
                colorsByDay.getValue(day).map { it.sourceColor }.toSet(),
                "each occurrence day must include the recurring calendar color",
            )
        }
    }

    @Test
    fun observeVisibleDotColorsByDay_movedOverrideDotsTheDayItLandedOn() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://override-colors")
        val green = CalendarSourceColor(0xFF43A047.toInt())
        seedCalendar(account, calendarId, green)

        // DAILY×3 from 06-15, with the 06-16 instance pushed to 06-19: the month grid must follow the
        // planning view and dot 06-19, leaving 06-16 bare.
        val master = recurringColorMaster(
            eventId = EventId("event://override-moved"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 16, 10, 0),
            movedTo = LocalDateTime(2026, 6, 19, 10, 0),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            setOf(
                LocalDateTime(2026, 6, 15, 0, 0).date,
                LocalDateTime(2026, 6, 17, 0, 0).date,
                LocalDateTime(2026, 6, 19, 0, 0).date,
            ),
            colorsByDay.keys,
        )
    }

    @Test
    fun observeVisibleDotColorsByDay_overrideMovedPastTheSeriesEndStillDotsItsDay() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://override-colors")
        val green = CalendarSourceColor(0xFF43A047.toInt())
        seedCalendar(account, calendarId, green)

        // The whole series ends in June, so only the override range branch can bring the master back
        // for an August window.
        val master = recurringColorMaster(
            eventId = EventId("event://override-far-away"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 16, 10, 0),
            movedTo = LocalDateTime(2026, 8, 20, 10, 0),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 8, 17, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 8, 24, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(setOf(LocalDateTime(2026, 8, 20, 0, 0).date), colorsByDay.keys)
        assertEquals(
            setOf(green.argb),
            colorsByDay.getValue(LocalDateTime(2026, 8, 20, 0, 0).date).map { it.sourceColor }.toSet(),
        )
    }

    @Test
    fun observeVisibleDotColorsByDay_cancelledOverrideClearsItsDay() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://override-colors")
        val green = CalendarSourceColor(0xFF43A047.toInt())
        seedCalendar(account, calendarId, green)

        val master = recurringColorMaster(
            eventId = EventId("event://override-cancelled"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(
            master.id,
            originalStart = LocalDateTime(2026, 6, 16, 10, 0),
            status = EventStatus.CANCELLED,
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 22, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(
            setOf(LocalDateTime(2026, 6, 15, 0, 0).date, LocalDateTime(2026, 6, 17, 0, 0).date),
            colorsByDay.keys,
            "a cancelled override deletes its occurrence, so its day loses the dot",
        )
    }

    @Test
    fun observeVisibleDotColorsByDay_movedFloatingOverrideDotsItsWallClockDayInAnyZone() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://override-colors")
        val green = CalendarSourceColor(0xFF43A047.toInt())
        seedCalendar(account, calendarId, green)

        // Floating series (no zone at all) whose 06-16 instance is pushed to 08-20, read from a zone far
        // from UTC: a floating instance is rendered as wall-clock, so 08-20 is dotted there too.
        val master = recurringColorMaster(
            eventId = EventId("event://override-floating"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, until = RecurrenceUntil.Floating(LocalDateTime(2026, 6, 17, 10, 0))),
            floating = true,
        )
        val override = overrideEntity(
            masterId = master.id,
            originalStart = LocalDateTime(2026, 6, 16, 10, 0),
            movedTo = LocalDateTime(2026, 8, 20, 10, 0),
            floating = true,
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val zone = TimeZone.of("Pacific/Honolulu") // UTC-10, no DST
        val movedDayColors = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 8, 17, 0, 0).toInstant(zone),
            end = LocalDateTime(2026, 8, 24, 0, 0).toInstant(zone),
            timeZone = zone,
        ).first()

        assertEquals(setOf(LocalDateTime(2026, 8, 20, 0, 0).date), movedDayColors.keys)
        assertEquals(
            setOf(green.argb),
            movedDayColors.getValue(LocalDateTime(2026, 8, 20, 0, 0).date).map { it.sourceColor }.toSet(),
        )

        val vacatedDayColors = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 16, 0, 0).toInstant(zone),
            end = LocalDateTime(2026, 6, 17, 0, 0).toInstant(zone),
            timeZone = zone,
        ).first()

        assertEquals(emptySet(), vacatedDayColors.keys, "the slot the instance left keeps no dot")
    }

    @Test
    fun observeVisibleDotColorsByDay_overrideOutsideAPartialDayWindowIsNotDotted() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://override-colors")
        seedCalendar(account, calendarId, CalendarSourceColor(0xFF43A047.toInt()))

        // The relation carries every override of a selected master, so a window narrower than a day must
        // still be honoured: nothing happens between 12:00 and 13:00 on 06-19.
        val master = recurringColorMaster(
            eventId = EventId("event://override-partial-day"),
            calendarId = calendarId,
            dtStart = LocalDateTime(2026, 6, 15, 10, 0),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 3),
        )
        val override = overrideEntity(
            masterId = master.id,
            originalStart = LocalDateTime(2026, 6, 16, 10, 0),
            movedTo = LocalDateTime(2026, 6, 19, 10, 0),
        )
        eventDao().upsert(listOf(EventWithRawIcs(master, "", listOf(override))))

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 19, 12, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 19, 13, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        assertEquals(emptySet(), colorsByDay.keys)
    }

    @Test
    fun observeVisibleDotColorsByDay_reprojectsAnchoredEventAcrossDateBoundary() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://tz")
        val purple = CalendarSourceColor(0xFF8E24AA.toInt())
        val displayZone = TimeZone.of("Europe/Paris")
        seedCalendar(account, calendarId, purple)

        eventDao().upsert(
            listOf(
                EventWithRawIcs(
                    timedEvent(
                        id = EventId("event://utc-late"),
                        calendarId = calendarId,
                        start = LocalDateTime(2026, 6, 15, 23, 30),
                        end = LocalDateTime(2026, 6, 16, 0, 30),
                    ),
                    "",
                ),
            ),
        )

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 17, 0, 0).toInstant(TimeZone.UTC),
            timeZone = displayZone,
        ).first()

        val day16 = LocalDateTime(2026, 6, 16, 0, 0).date
        assertEquals(setOf(day16), colorsByDay.keys, "UTC late event must land on day 16 in Europe/Paris")
        assertEquals(setOf(purple.argb), colorsByDay.getValue(day16).map { it.sourceColor }.toSet())
    }

    @Test
    fun observeVisibleDotColorsByDay_keepsFloatingWallClockPlacementInDisplayZone() = runTest {
        val account = AccountId(1)
        val calendarId = CalendarId("calendar://floating")
        val amber = CalendarSourceColor(0xFFFFB300.toInt())
        val displayZone = TimeZone.of("Europe/Paris")
        seedCalendar(account, calendarId, amber)

        eventDao().upsert(
            listOf(
                EventWithRawIcs(
                    floatingTimedEvent(
                        id = EventId("event://floating-night"),
                        calendarId = calendarId,
                        start = LocalDateTime(2026, 6, 15, 23, 30),
                        end = LocalDateTime(2026, 6, 16, 0, 30),
                    ),
                    "",
                ),
            ),
        )

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(displayZone),
            end = LocalDateTime(2026, 6, 17, 0, 0).toInstant(displayZone),
            timeZone = displayZone,
        ).first()

        val day15 = LocalDateTime(2026, 6, 15, 0, 0).date
        val day16 = LocalDateTime(2026, 6, 16, 0, 0).date
        assertEquals(setOf(day15, day16), colorsByDay.keys)
        assertEquals(setOf(amber.argb), colorsByDay.getValue(day15).map { it.sourceColor }.toSet())
        assertEquals(setOf(amber.argb), colorsByDay.getValue(day16).map { it.sourceColor }.toSet())
    }

    @Test
    fun observeVisibleDotColorsByDay_ordersColorsLikePerDayEventSlices() = runTest {
        val account = AccountId(1)
        val floatingCalendarId = CalendarId("calendar://floating-08")
        val anchoredCalendarId = CalendarId("calendar://anchored-09")
        val floatingColor = CalendarSourceColor(0xFFFFB300.toInt())
        val anchoredColor = CalendarSourceColor(0xFF1E88E5.toInt())
        seedCalendar(account, floatingCalendarId, floatingColor)
        seedCalendar(account, anchoredCalendarId, anchoredColor)

        eventDao().upsert(
            listOf(
                EventWithRawIcs(
                    floatingTimedEvent(
                        id = EventId("event://floating-08"),
                        calendarId = floatingCalendarId,
                        start = LocalDateTime(2026, 6, 15, 8, 0),
                        end = LocalDateTime(2026, 6, 15, 9, 0),
                    ),
                    "",
                ),
                EventWithRawIcs(
                    timedEvent(
                        id = EventId("event://anchored-09"),
                        calendarId = anchoredCalendarId,
                        start = LocalDateTime(2026, 6, 15, 9, 0),
                        end = LocalDateTime(2026, 6, 15, 10, 0),
                    ),
                    "",
                ),
            ),
        )

        val colorsByDay = repository.observeVisibleDotColorsByDay(
            accountIds = setOf(account),
            start = LocalDateTime(2026, 6, 15, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 16, 0, 0).toInstant(TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).first()

        val day15 = LocalDateTime(2026, 6, 15, 0, 0).date
        assertEquals(
            listOf(floatingColor.argb, anchoredColor.argb),
            colorsByDay.getValue(day15).map { it.sourceColor },
            "per-day color order must follow event slice ordering (displayStart), not anchored-first SQL ordering",
        )
    }

    private fun timedEvent(
        id: EventId,
        calendarId: CalendarId,
        start: LocalDateTime,
        end: LocalDateTime,
        colorArgb: Int? = null,
    ): EventEntity = EventEntity(
        id = id,
        calendarId = calendarId,
        content = EventContentEntity(
            summary = id.url,
            timing = EventTimingEntity(
                dtStart = start,
                dtEndEffective = end,
                startTimeZone = TimeZone.UTC.id,
                endTimeZone = TimeZone.UTC.id,
                dtStartInstantMs = start.toInstant(TimeZone.UTC).toEpochMilliseconds(),
                dtEndInstantMs = end.toInstant(TimeZone.UTC).toEpochMilliseconds(),
            ),
            colorArgb = colorArgb,
        ),
        etag = "1",
    )

    private fun floatingTimedEvent(
        id: EventId,
        calendarId: CalendarId,
        start: LocalDateTime,
        end: LocalDateTime,
    ): EventEntity = EventEntity(
        id = id,
        calendarId = calendarId,
        content = EventContentEntity(
            summary = id.url,
            timing = EventTimingEntity(
                dtStart = start,
                dtEndEffective = end,
                startTimeZone = null,
                endTimeZone = null,
                dtStartInstantMs = null,
                dtEndInstantMs = null,
            ),
        ),
        etag = "1",
    )
}
