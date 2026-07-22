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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.local.projection.LocalEventRef
import com.infomaniak.multiplatform_calendar.core.data.local.relation.EventWithCalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

@Dao
internal interface EventDao {

    @Query("SELECT * FROM events WHERE calendarId = :calendarId ORDER BY dtStart ASC")
    fun observeEvents(calendarId: CalendarId): Flow<List<EventEntity>>

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
    fun observeVisibleInRange(
        accountIds: Set<AccountId>,
        startInstantMs: Long,
        endInstantMs: Long,
        startLocalDateTime: LocalDateTime,
        endLocalDateTime: LocalDateTime,
    ): Flow<List<EventWithCalendarEntity>>

    @Upsert
    suspend fun upsert(eventDao: List<EventEntity>)

    @Query("SELECT id FROM events WHERE calendarId = :calendarId AND id IN (:eventIds)")
    suspend fun getExistingEventIds(calendarId: CalendarId, eventIds: List<EventId>): List<EventId>

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
    suspend fun getEventRefsInRange(
        calendarId: CalendarId,
        startInstantMs: Long,
        endInstantMs: Long,
        startLocalDateTime: LocalDateTime,
        endLocalDateTime: LocalDateTime,
    ): List<LocalEventRef>

    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    suspend fun getEvent(eventId: EventId): EventEntity?


    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    fun observeEventWithCalendar(eventId: EventId): Flow<EventWithCalendarEntity?>

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: EventId)

    @Query("DELETE FROM events WHERE calendarId = :calendarId AND id IN (:eventIds)")
    suspend fun deleteEvents(calendarId: CalendarId, eventIds: List<EventId>)
}
