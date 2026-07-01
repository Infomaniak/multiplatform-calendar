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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.infomaniak.multiplatform_calendar.core.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AccountDao {

    @Query("SELECT * FROM calendars WHERE accountId = :accountId ORDER BY displayName ASC")
    fun getByAccountId(accountId: Long): Flow<List<CalendarEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity)

    @Query(
        """
        SELECT calendars.accountId FROM events
        INNER JOIN calendars ON events.calendarId = calendars.id
        WHERE events.id = :eventId LIMIT 1
        """,
    )
    suspend fun getAccountIdByEventId(eventId: EventId): AccountId?


    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun delete(accountId: AccountId)
}
