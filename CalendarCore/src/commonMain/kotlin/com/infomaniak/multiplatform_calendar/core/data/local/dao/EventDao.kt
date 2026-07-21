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
     * Two branches, unioned:
     * - **Anchored events** (zoned / UTC / all-day): comparison on absolute UTC epoch milliseconds.
     *   Correct across mixed time-zones since bounds are absolute.
     * - **Floating events** (RFC 5545 FORM #1, [EventTimingEntity.dtStartInstantMs] `IS NULL`): comparison
     *   on wall-clock strings, using [startLocalDateTime]/[endLocalDateTime] which are the range bounds re-interpreted
     *   in the recipient's *current* zone. This branch re-anchors automatically on device zone
     *   change (travel, DST) — a floating event has no fixed absolute instant by definition.
     */
    @Transaction
    @Query(
        """
        SELECT event.* FROM events event
        INNER JOIN calendars calendar ON event.calendarId = calendar.id
        WHERE calendar.accountId IN(:accountIds)
          AND calendar.isVisible = 1
          AND (
            (event.dtStartInstantMs IS NOT NULL
              AND event.dtStartInstantMs < :endInstantMs
              AND event.dtEndInstantMs >= :startInstantMs)
            OR
            (event.dtStartInstantMs IS NULL
              AND event.dtStart < :endLocalDateTime
              AND event.dtEndEffective >= :startLocalDateTime)
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

    @Transaction
    open suspend fun upsertEventsWithRawIcs(events: List<EventEntity>, rawIcs: List<EventRawIcsEntity>) {
        upsertEvents(events)
        upsertRawIcs(rawIcs)
    }

    @Upsert
    protected abstract suspend fun upsertEvents(events: List<EventEntity>)

    @Upsert
    protected abstract suspend fun upsertRawIcs(rawIcs: List<EventRawIcsEntity>)

    @Query("SELECT id FROM events WHERE calendarId = :calendarId AND id IN (:eventIds)")
    abstract suspend fun getExistingEventIds(calendarId: CalendarId, eventIds: List<EventId>): List<EventId>

    @Query(
        """
        SELECT id, etag FROM events
        WHERE calendarId = :calendarId
          AND (
            (dtStartInstantMs IS NOT NULL
              AND dtStartInstantMs < :endInstantMs
              AND dtEndInstantMs >= :startInstantMs)
            OR
            (dtStartInstantMs IS NULL
              AND dtStart < :endLocalDateTime
              AND dtEndEffective >= :startLocalDateTime)
          )
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

    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    abstract fun observeEventWithCalendar(eventId: EventId): Flow<EventWithCalendarEntity?>

    @Query("DELETE FROM events WHERE id = :eventId")
    abstract suspend fun deleteEvent(eventId: EventId)

    @Query("DELETE FROM events WHERE calendarId = :calendarId AND id IN (:eventIds)")
    abstract suspend fun deleteEvents(calendarId: CalendarId, eventIds: List<EventId>)
}
