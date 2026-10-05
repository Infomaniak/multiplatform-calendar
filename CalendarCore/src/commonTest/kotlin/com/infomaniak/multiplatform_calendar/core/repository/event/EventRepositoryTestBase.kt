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

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AttendeeEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.OrganizerEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.RecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.mapper.toRecurrenceBoundsEntity
import com.infomaniak.multiplatform_calendar.core.data.repository.EventRepository
import com.infomaniak.multiplatform_calendar.core.dataset.RecordingCrashReport
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSourceColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AlarmListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.AttendeeRole
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Organizer
import com.infomaniak.multiplatform_calendar.core.domain.model.event.ParticipationStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm.EventAlarm
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceBoundKind
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.extensions.toICalLocalDateTime
import com.infomaniak.multiplatform_calendar.core.extensions.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.core.utils.upsert
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Shared fixture of the [EventRepository] tests: an in-memory database and a [FakeCaldavClient]. */
internal abstract class EventRepositoryTestBase : RobolectricTestsBase() {

    protected lateinit var database: CalendarDatabase
    protected lateinit var repository: EventRepository
    protected lateinit var fakeCaldav: FakeCaldavClient
    private lateinit var crashReport: RecordingCrashReport

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(
            driver = DatabaseProviderFactory.driver(),
            inMemory = true,
        )
        fakeCaldav = FakeCaldavClient()
        crashReport = RecordingCrashReport()
        repository = EventRepository(
            accountDao = database.accountDao(),
            caldavClient = fakeCaldav,
            eventDao = database.eventDao(),
            crashReport = crashReport,
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    protected fun dailyMasterEntity(eventId: EventId, calendarId: CalendarId): EventEntity {
        val dtStart = LocalDateTime(2026, 6, 15, 10, 0)
        val dtEnd = LocalDateTime(2026, 6, 15, 11, 0)
        return EventEntity(
            id = eventId,
            calendarId = calendarId,
            content = EventContentEntity(
                summary = "Daily 10-11",
                timing = EventTimingEntity(
                    dtStart = dtStart,
                    dtEndEffective = dtEnd,
                    startTimeZone = TimeZone.UTC.id,
                    endTimeZone = TimeZone.UTC.id,
                    dtStartInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds(),
                    dtEndInstantMs = dtEnd.toInstant(TimeZone.UTC).toEpochMilliseconds(),
                ),
            ),
            rrule = RecurrenceRule(freq = Frequency.Daily, occurrenceCount = 5),
            hasRecurrence = true,
            recurrenceBounds = RecurrenceBoundsEntity(
                firstOccurrenceInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds(),
                recurrenceBoundKind = RecurrenceBoundKind.FiniteDeferred,
            ),
            etag = "1",
        )
    }

    protected fun overrideEntity(
        masterId: EventId,
        originalStart: LocalDateTime,
        movedTo: LocalDateTime = originalStart,
        status: EventStatus? = null,
        floating: Boolean = false,
    ): EventOverrideEntity {
        val originalEnd = LocalDateTime(originalStart.date, LocalTime(originalStart.hour + 1, originalStart.minute))
        val movedEnd = LocalDateTime(movedTo.date, LocalTime(movedTo.hour + 1, movedTo.minute))
        return EventOverrideEntity(
            masterId = masterId,
            recurrenceKey = if (floating) {
                RecurrenceKey.Floating(originalStart)
            } else {
                RecurrenceKey.Utc(originalStart.toInstant(TimeZone.UTC))
            },
            recurrenceIdValue = if (floating) {
                originalStart.toICalLocalDateTime()
            } else {
                originalStart.toInstant(TimeZone.UTC).toICalUtcDateTime()
            },
            recurrenceIdTzid = null,
            originalStartInstantMs = originalStart.toInstant(TimeZone.UTC).toEpochMilliseconds().takeUnless { floating },
            originalEndInstantMs = originalEnd.toInstant(TimeZone.UTC).toEpochMilliseconds().takeUnless { floating },
            originalStartLocalDateTime = originalStart,
            originalEndLocalDateTime = originalEnd,
            content = EventContentEntity(
                summary = "Moved instance",
                status = status,
                timing = EventTimingEntity(
                    dtStart = movedTo,
                    dtEndEffective = movedEnd,
                    startTimeZone = TimeZone.UTC.id.takeUnless { floating },
                    endTimeZone = TimeZone.UTC.id.takeUnless { floating },
                    dtStartInstantMs = movedTo.toInstant(TimeZone.UTC).toEpochMilliseconds().takeUnless { floating },
                    dtEndInstantMs = movedEnd.toInstant(TimeZone.UTC).toEpochMilliseconds().takeUnless { floating },
                ),
            ),
        )
    }

    protected fun icalUtc(year: Int, month: Int, day: Int) =
        IcalDateValue.Zoned(LocalDateTime(year, month, day, 10, 0).toInstant(TimeZone.UTC), TimeZone.UTC.id)

    protected fun occurrenceOf(masterId: EventId, start: LocalDateTime) = OccurrenceId.Recurrence(
        masterId = masterId,
        recurrenceKey = RecurrenceKey.Utc(start.toInstant(TimeZone.UTC)),
    )

    protected fun recurringColorMaster(
        eventId: EventId,
        calendarId: CalendarId = CALENDAR_ID,
        dtStart: LocalDateTime,
        rrule: RecurrenceRule? = null,
        rDates: List<IcalDateValue> = emptyList(),
        floating: Boolean = false,
    ): EventEntity {
        val dtEnd = LocalDateTime(dtStart.date, LocalTime(dtStart.hour + 1, dtStart.minute))
        val timing = EventTimingEntity(
            dtStart = dtStart,
            dtEndEffective = dtEnd,
            startTimeZone = TimeZone.UTC.id.takeUnless { floating },
            endTimeZone = TimeZone.UTC.id.takeUnless { floating },
            dtStartInstantMs = dtStart.toInstant(TimeZone.UTC).toEpochMilliseconds().takeUnless { floating },
            dtEndInstantMs = dtEnd.toInstant(TimeZone.UTC).toEpochMilliseconds().takeUnless { floating },
        )
        return EventEntity(
            id = eventId,
            calendarId = calendarId,
            content = EventContentEntity(
                summary = "Daily recurring",
                timing = timing,
            ),
            rrule = rrule,
            rDates = rDates,
            hasRecurrence = true,
            recurrenceBounds = checkNotNull(
                toRecurrenceBoundsEntity(
                    timing = timing,
                    recurrenceRule = rrule,
                    rDates = rDates,
                ),
            ),
            etag = "1",
        )
    }

    protected suspend fun seedCalendar(
        accountId: AccountId = ACCOUNT_ID,
        calendarId: CalendarId = CALENDAR_ID,
        color: CalendarSourceColor? = null,
    ) {
        database.accountDao().insert(AccountEntity(id = accountId))
        database.calendarDao().upsert(
            listOf(
                CalendarEntity(
                    id = calendarId,
                    accountId = accountId,
                    displayName = "cal",
                    color = color,
                    isVisible = true,
                ),
            ),
        )
    }

    protected fun editData(
        title: String,
        calendarId: CalendarId = CALENDAR_ID,
        recurrence: RecurrenceRule? = null,
        alarms: List<EventAlarm> = emptyList(),
        start: LocalDateTime = LocalDateTime(2026, 6, 15, 10, 0),
        end: LocalDateTime = LocalDateTime(2026, 6, 15, 11, 0),
        timeZone: TimeZone? = TimeZone.UTC,
        isAllDay: Boolean = false,
        attendees: List<AttendeeEdit> = emptyList(),
        organizer: Organizer? = null,
    ) = EventEditData(
        title = title,
        timing = EventTiming(
            start = start,
            end = end,
            startTimeZone = timeZone,
            endTimeZone = timeZone,
            isAllDay = isAllDay,
            recurrenceRule = recurrence,
        ),
        location = null,
        description = null,
        timeBlocking = null,
        calendarId = calendarId,
        eventColor = null,
        alarms = AlarmListEdit.Replace(alarms),
        attendees = attendees,
        organizer = organizer,
    )

    protected fun eventDao() = database.eventDao()

    protected companion object {
        val ACCOUNT_ID = AccountId(1)
        val CALENDAR_ID = CalendarId("calendar://main")
        val CREDENTIALS = DavAccount(baseUrl = "https://cal/", username = "u", password = "p")
        val OWNER = OrganizerEntity(email = "owner@example.com", displayName = "Owner")
        val ALICE = AttendeeEntity(
            email = "alice@example.com",
            status = ParticipationStatus.Accepted,
            role = AttendeeRole.Requested,
        )
        val PARIS = TimeZone.of("Europe/Paris")
    }
}
