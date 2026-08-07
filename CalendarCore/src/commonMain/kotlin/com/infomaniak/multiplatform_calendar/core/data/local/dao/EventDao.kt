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
package com.infomaniak.multiplatform_calendar.core.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventRawIcsEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventCalendarColorInRange
import com.infomaniak.multiplatform_calendar.core.data.local.projection.LocalEventRef
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

@Dao
internal abstract class EventDao {

    @Query("SELECT * FROM events WHERE calendarId = :calendarId ORDER BY dtStart ASC")
    abstract fun observeEvents(calendarId: CalendarId): Flow<List<EventEntity>>

    /**
     * Events (with their parent calendar) from all *visible* calendars of [accountId] that overlap
     * the [`[startInstantMs, endInstantMs[`] range. An event overlaps when it starts before [endInstantMs]
     * and its resolved end ([EventTimingEntity.dtEndInstantMs], which already accounts for `DTEND`/`DURATION`)
     * is at/after [startInstantMs].
     *
     * Non-recurring events use two branches, unioned:
     * - **Anchored events** (zoned / UTC / all-day): comparison on absolute UTC epoch milliseconds.
     *   Correct across mixed time-zones since bounds are absolute.
     * - **Floating events** (RFC 5545 FORM #1, [EventTimingEntity.dtStartInstantMs] `IS NULL`): comparison
     *   on wall-clock strings, using [startLocalDateTime]/[endLocalDateTime] which are the range bounds re-interpreted
     *   in the recipient's *current* zone. This branch re-anchors automatically on device zone
     *   change (travel, DST) — a floating event has no fixed absolute instant by definition.
     *
     * **Recurring masters** (`rrule IS NOT NULL`) match on the *whole series* bounds populated at sync
     * (see `recurrenceBounds`), not just their first occurrence. Two symmetric branches:
     * - **Anchored series**: `firstOccurrenceInstantMs` as low bound, `lastPossibleOccurrenceEndInstantMs`
     *   (an over-approximation of the last end) as high bound. `Infinite`/`FiniteDeferred` series have an open
     *   upper bound. Masters are returned whenever the series *could* overlap the window; the expander
     *   later materialises the actual occurrences and drops any that fall outside.
     * - **Floating series**: `dtStart` as low bound (first occurrence == `DTSTART` for RRULE-only) and
     *   `lastOccurrenceEndLocalDateTime` as high bound, both wall-clock.
     */
    @Transaction
    @Query(
        """
        SELECT event.* FROM events event
        INNER JOIN calendars calendar ON event.calendarId = calendar.id
        WHERE calendar.accountId IN(:accountIds)
          AND calendar.isVisible = 1
          AND (
            (event.rrule IS NULL AND $ANCHORED_TIMING)
            OR (event.rrule IS NULL AND $FLOATING_TIMING)
            OR $RECURRING_ANCHORED
            OR $RECURRING_FLOATING
          )
        ORDER BY event.dtStartInstantMs IS NULL, event.dtStartInstantMs ASC, event.dtStart ASC
        """,
    )
    abstract fun observeVisibleInRange(
        accountIds: Set<AccountId>,
        startInstantMs: Long,
        endInstantMs: Long,
        startLocalDateTime: LocalDateTime,
        endLocalDateTime: LocalDateTime,
    ): Flow<List<EventWithCalendarEntity>>

    /**
     * Same *visible calendars* + *range overlap* filter as [observeVisibleInRange], but returns only the
     * lightweight [EventCalendarColorInRange] projection (owning calendar color + wall-clock bounds), never a
     * full event. Meant to feed a per-day calendar-color map (e.g. a month grid): no event body, attendees or
     * raw ICS is read, so large months stay cheap. Day placement is done in Kotlin from the wall-clock columns
     * (mirroring `EventTiming.startIn`/`endIn`) since day boundaries depend on the caller's display zone.
     */
    @Query(
        """
        SELECT event.id AS eventId,
               calendar.color AS colorArgb,
               event.dtStart AS dtStart,
               event.dtEndEffective AS dtEndEffective,
               event.startTimeZone AS startZoneId,
               event.endTimeZone AS endZoneId,
               event.isAllDay AS isAllDay,
               event.rrule AS rrule
        FROM events event
        INNER JOIN calendars calendar ON event.calendarId = calendar.id
        WHERE calendar.accountId IN(:accountIds)
          AND calendar.isVisible = 1
          AND (
            (event.rrule IS NULL AND $ANCHORED_TIMING)
            OR (event.rrule IS NULL AND $FLOATING_TIMING)
            OR $RECURRING_ANCHORED
            OR $RECURRING_FLOATING
          )
        ORDER BY event.dtStartInstantMs IS NULL, event.dtStartInstantMs ASC, event.dtStart ASC
        """,
    )
    abstract fun observeVisibleCalendarColorsInRange(
        accountIds: Set<AccountId>,
        startInstantMs: Long,
        endInstantMs: Long,
        startLocalDateTime: LocalDateTime,
        endLocalDateTime: LocalDateTime,
    ): Flow<List<EventCalendarColorInRange>>

    @Transaction
    open suspend fun upsertEventsWithRawIcs(events: List<EventEntity>, rawIcs: List<EventRawIcsEntity>) {
        upsertEvents(events)
        upsertRawIcs(rawIcs)
    }

    /** Single-event convenience for the edit paths, avoiding a list allocation + re-partition per event. */
    @Transaction
    open suspend fun upsertEventWithRawIcs(event: EventEntity, rawIcs: String) {
        upsertEvents(listOf(event))
        upsertRawIcs(listOf(EventRawIcsEntity(eventId = event.id, rawIcs = rawIcs)))
    }

    @Upsert
    protected abstract suspend fun upsertEvents(events: List<EventEntity>)

    @Upsert
    protected abstract suspend fun upsertRawIcs(rawIcs: List<EventRawIcsEntity>)

    @Query("SELECT id FROM events WHERE calendarId = :calendarId AND id IN (:eventIds)")
    abstract suspend fun getExistingEventIds(calendarId: CalendarId, eventIds: List<EventId>): List<EventId>

    @Query(
        """
        SELECT event.id, event.etag FROM events event
        WHERE event.calendarId = :calendarId AND ($ANCHORED_TIMING OR $FLOATING_TIMING)
        """,
    )
    abstract suspend fun getEventRefsInRange(
        calendarId: CalendarId,
        startInstantMs: Long,
        endInstantMs: Long,
        startLocalDateTime: LocalDateTime,
        endLocalDateTime: LocalDateTime,
    ): List<LocalEventRef>

    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    abstract suspend fun getEvent(eventId: EventId): EventEntity?

    @Query("SELECT rawIcs FROM event_raw_ics WHERE eventId = :eventId LIMIT 1")
    abstract suspend fun getRawIcs(eventId: EventId): String?

    /**
     * Reads the event row and its raw ICS in a single transaction so callers get a consistent
     * snapshot: a concurrent sync upsert can't commit a new event/raw-ICS pair between the two
     * reads, which would otherwise let an edit patch fresh ICS with a stale entity/ETag.
     */
    @Transaction
    open suspend fun getEventWithRawIcs(eventId: EventId): EventWithRawIcs? {
        val event = getEvent(eventId) ?: return null
        val rawIcs = getRawIcs(eventId) ?: return null
        return EventWithRawIcs(event, rawIcs)
    }

    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    abstract fun observeEventWithCalendar(eventId: EventId): Flow<EventWithCalendarEntity?>

    @Query("DELETE FROM events WHERE id = :eventId")
    abstract suspend fun deleteEvent(eventId: EventId)

    @Query("DELETE FROM events WHERE calendarId = :calendarId AND id IN (:eventIds)")
    abstract suspend fun deleteEvents(calendarId: CalendarId, eventIds: List<EventId>)

    private companion object {

        /** Non-recurring / master timing overlap on absolute UTC epoch ms (zoned / UTC / all-day rows). */
        private const val ANCHORED_TIMING = """(
            event.dtStartInstantMs IS NOT NULL
              AND event.dtStartInstantMs < :endInstantMs
              AND event.dtEndInstantMs >= :startInstantMs)"""

        /** Non-recurring / master timing overlap on wall-clock strings (floating DATE-TIME rows). */
        private const val FLOATING_TIMING = """(
            event.dtStartInstantMs IS NULL
              AND event.dtStart < :endLocalDateTime
              AND event.dtEndEffective >= :startLocalDateTime)"""

        /** Anchored recurrence set: series bounds in absolute epoch ms; open upper bound unless `Finite`. */
        private const val RECURRING_ANCHORED = """(
            event.rrule IS NOT NULL
              AND event.firstOccurrenceInstantMs IS NOT NULL
              AND event.firstOccurrenceInstantMs < :endInstantMs
              AND (event.recurrenceBoundKind != 'Finite'
                OR event.lastPossibleOccurrenceEndInstantMs >= :startInstantMs))"""

        /** Floating recurrence set: series bounds in wall-clock; open upper bound unless `Finite`. */
        private const val RECURRING_FLOATING = """(
            event.rrule IS NOT NULL
              AND event.firstOccurrenceInstantMs IS NULL
              AND event.dtStart < :endLocalDateTime
              AND (event.recurrenceBoundKind != 'Finite'
                OR event.lastOccurrenceEndLocalDateTime >= :startLocalDateTime))"""
    }
}
