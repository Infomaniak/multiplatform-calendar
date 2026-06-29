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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
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
     * the [start, end[ range. An event overlaps when it starts before [end] and its resolved end
     * ([EventEntity.dtEndEffective], which already accounts for `DTEND`/`DURATION`) is at/after [start].
     *
     * Note: bounds are compared as stored wall-clock values (UTC assumed) — see timezone TODO.
     */
    @Transaction
    @Query(
        """
        SELECT event.* FROM events event
        INNER JOIN calendars calendar ON event.calendarId = calendar.id
        WHERE calendar.accountId = :accountId
          AND calendar.isVisible = 1
          AND event.dtStart < :end
          AND event.dtEndEffective >= :start
        ORDER BY event.dtStart ASC
        """,
    )
    fun observeVisibleInRange(
        accountId: AccountId,
        start: LocalDateTime,
        end: LocalDateTime,
    ): Flow<List<EventWithCalendarEntity>>

    @Transaction
    @Query(
        """
        SELECT event.* FROM events event
        INNER JOIN calendars calendar ON event.calendarId = calendar.id
        WHERE calendar.accountId IN(:accountIds)
          AND calendar.isVisible = 1
          AND event.dtStart < :end
          AND event.dtEndEffective >= :start
        ORDER BY event.dtStart ASC
        """,
    )
    fun observeVisibleInRange(
        accountIds: Set<AccountId>,
        start: LocalDateTime,
        end: LocalDateTime,
    ): Flow<List<EventWithCalendarEntity>>

    @Upsert
    suspend fun upsert(eventDao: List<EventEntity>)

    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    suspend fun getEvent(eventId: EventId): EventEntity?

    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    fun observeEventWithCalendar(eventId: EventId): Flow<EventWithCalendarEntity?>

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: EventId)
}

