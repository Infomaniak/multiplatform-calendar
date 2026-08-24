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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventRawIcsEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventUpsertBatch
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventWithRawIcs
import com.infomaniak.multiplatform_calendar.core.data.local.entity.toUpsertBatch
import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventCalendarColorInRange
import com.infomaniak.multiplatform_calendar.core.data.local.projection.LocalEventRef
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
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
     * - **Anchored events** (zoned / UTC): comparison on absolute UTC epoch milliseconds.
     *   Correct across mixed time-zones since bounds are absolute.
     * - **Wall-clock events**: comparison on wall-clock strings, using [startLocalDateTime]/[endLocalDateTime]
     *   which are the range bounds re-interpreted in the recipient's *current* zone. This branch re-anchors
     *   automatically on device zone change (travel, DST). It covers **floating** events (RFC 5545 FORM #1,
     *   [EventTimingEntity.dtStartInstantMs] `IS NULL`), which have no fixed absolute instant by definition,
     *   and **all-day** events, which are stored at UTC midnight but rendered as-is in the reader's zone.
     *
     * **Recurring masters** (`hasRecurrence = 1`) match on the *whole series* bounds populated at sync
     * (see `recurrenceBounds`), not just their first occurrence. Two symmetric branches:
     * - **Anchored series**: `firstOccurrenceInstantMs` as low bound, `lastPossibleOccurrenceEndInstantMs`
     *   (an over-approximation of the last end) as high bound. `Infinite`/`FiniteDeferred` series have an open
     *   upper bound. Masters are returned whenever the series *could* overlap the window; the expander
     *   later materialises the actual occurrences and drops any that fall outside.
     * - **Floating series**: `dtStart` as low bound (first occurrence == `DTSTART` for RRULE-only) and
     *   `lastOccurrenceEndLocalDateTime` as high bound, both wall-clock.
     *
     * **Overridden instances** finally add a third pair of branches: a `RECURRENCE-ID` override that
     * was *moved* can land outside the series bounds entirely, so the master is also returned when
     * one of its overrides overlaps the window at its effective position.
     */
    @Transaction
    @Query(
        """
        SELECT event.* FROM events event
        INNER JOIN calendars calendar ON event.calendarId = calendar.id
        WHERE calendar.accountId IN(:accountIds)
          AND calendar.isVisible = 1
          AND (
            (event.hasRecurrence = 0 AND $ANCHORED_TIMING)
            OR (event.hasRecurrence = 0 AND $FLOATING_TIMING)
            OR $RECURRING_ANCHORED
            OR $RECURRING_FLOATING
            OR $OVERRIDE_ANCHORED
            OR $OVERRIDE_FLOATING
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
     *
     * Overrides ride along through the same batched relation as [observeVisibleInRange], projected down to
     * [OverrideCalendarColorInRange], so a moved instance dots the day it landed on rather than the one it left.
     */
    @Transaction
    @Query(
        """
        SELECT event.id AS eventId,
               event.calendarId AS calendarId,
               calendar.color AS colorArgb,
               event.dtStart AS dtStart,
               event.dtEndEffective AS dtEndEffective,
               event.startTimeZone AS startZoneId,
               event.endTimeZone AS endZoneId,
               event.isAllDay AS isAllDay,
               event.rrule AS rrule,
               event.rDates AS rDates,
               event.exDates AS exDates
        FROM events event
        INNER JOIN calendars calendar ON event.calendarId = calendar.id
        WHERE calendar.accountId IN(:accountIds)
          AND calendar.isVisible = 1
          AND (
            (event.hasRecurrence = 0 AND $ANCHORED_TIMING)
            OR (event.hasRecurrence = 0 AND $FLOATING_TIMING)
            OR $RECURRING_ANCHORED
            OR $RECURRING_FLOATING
            OR $OVERRIDE_ANCHORED
            OR $OVERRIDE_FLOATING
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
    open suspend fun upsertEventsWithRawIcs(batch: EventUpsertBatch) {
        upsertEvents(batch.events)
        upsertRawIcs(batch.rawIcs)
        upsertOverrides(batch.overrides)
        // Overrides have no ETag of their own, so a server-side removal leaves no tombstone: the
        // incoming list is authoritative for its master, and whatever is missing from it is stale.
        val incomingKeys = batch.overrides.groupBy(EventOverrideEntity::masterId)
        for (event in batch.events) {
            deleteStaleOverridesOf(event.id, incomingKeys[event.id]?.map { it.recurrenceKey }.orEmpty())
        }
    }

    /** Single-event convenience for the edit paths, which write one resource at a time. */
    @Transaction
    open suspend fun upsertEventWithRawIcs(event: EventWithRawIcs) {
        upsertEventsWithRawIcs(listOf(event).toUpsertBatch())
    }

    @Upsert
    protected abstract suspend fun upsertEvents(events: List<EventEntity>)

    @Upsert
    protected abstract suspend fun upsertRawIcs(rawIcs: List<EventRawIcsEntity>)

    @Upsert
    protected abstract suspend fun upsertOverrides(overrides: List<EventOverrideEntity>)

    @Query("DELETE FROM event_overrides WHERE masterId = :masterId AND recurrenceKey NOT IN (:keptKeys)")
    protected abstract suspend fun deleteStaleOverridesOf(masterId: EventId, keptKeys: List<RecurrenceKey>)

    @Query("SELECT * FROM event_overrides WHERE masterId = :masterId ORDER BY recurrenceKey")
    abstract suspend fun getOverridesOf(masterId: EventId): List<EventOverrideEntity>

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

        /** Non-recurring / master timing overlap on absolute UTC epoch ms (zoned / UTC rows). */
        private const val ANCHORED_TIMING = """(
            event.isAllDay = 0
              AND event.dtStartInstantMs IS NOT NULL
              AND event.dtStartInstantMs < :endInstantMs
              AND event.dtEndInstantMs >= :startInstantMs)"""

        /**
         * Non-recurring / master timing overlap on wall-clock strings.
         *
         * Covers floating DATE-TIME rows **and all-day rows**: both render zone-independently
         * (`EventTiming.startIn` returns their wall-clock as-is when `startTimeZone` is null), so
         * matching them on the UTC-midnight instants they are stored at would disagree with what the
         * caller ends up displaying.
         */
        private const val FLOATING_TIMING = """(
            (event.isAllDay = 1 OR event.dtStartInstantMs IS NULL)
              AND event.dtStart < :endLocalDateTime
              AND event.dtEndEffective >= :startLocalDateTime)"""

        /** Anchored recurrence set: series bounds in absolute epoch ms; open upper bound unless `Finite`. */
        private const val RECURRING_ANCHORED = """(
            event.hasRecurrence = 1
              AND event.firstOccurrenceInstantMs IS NOT NULL
              AND event.firstOccurrenceInstantMs < :endInstantMs
              AND (event.recurrenceBoundKind != 'Finite'
                OR event.lastPossibleOccurrenceEndInstantMs >= :startInstantMs))"""

        /** Floating recurrence set: series bounds in wall-clock; open upper bound unless `Finite`. */
        private const val RECURRING_FLOATING = """(
            event.hasRecurrence = 1
              AND event.firstOccurrenceInstantMs IS NULL
              AND event.dtStart < :endLocalDateTime
              AND (event.recurrenceBoundKind != 'Finite'
                OR event.lastOccurrenceEndLocalDateTime >= :startLocalDateTime))"""

        /**
         * A moved override drags its master back in: the series bounds above only cover the
         * *theoretical* slots, so an instance pushed past `UNTIL` would otherwise never be read.
         * The reverse case needs no branch — a theoretical slot inside the window means the series
         * itself overlaps it, so [RECURRING_ANCHORED]/[RECURRING_FLOATING] already match.
         *
         * Both branches mirror [ANCHORED_TIMING]/[FLOATING_TIMING], on the override's *effective*
         * position. As for non-recurring rows, all-day overrides match on wall-clock so filtering
         * follows what the reader sees in their zone.
         */
        private const val OVERRIDE_ANCHORED = """(
            event.hasRecurrence = 1
              AND EXISTS(SELECT 1 FROM event_overrides override
                WHERE override.masterId = event.id
                  AND override.isAllDay = 0
                  AND override.dtStartInstantMs IS NOT NULL
                  AND override.dtStartInstantMs < :endInstantMs
                  AND override.dtEndInstantMs >= :startInstantMs))"""

        private const val OVERRIDE_FLOATING = """(
            event.hasRecurrence = 1
              AND EXISTS(SELECT 1 FROM event_overrides override
                WHERE override.masterId = event.id
                  AND (override.isAllDay = 1 OR override.dtStartInstantMs IS NULL)
                  AND override.dtStart < :endLocalDateTime
                  AND override.dtEndEffective >= :startLocalDateTime))"""
    }
}
