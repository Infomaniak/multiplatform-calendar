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
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import com.infomaniak.multiplatform_calendar.core.data.local.entity.CalendarEntity
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CalendarDao {

    @Query("SELECT * FROM calendars WHERE accountId = :accountId ORDER BY displayName ASC")
    fun observeByAccountId(accountId: AccountId): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE accountId IN(:accountIds) ORDER BY displayName ASC")
    fun observeByAccountIds(accountIds: Set<AccountId>): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE accountId = :accountId ORDER BY displayName ASC")
    suspend fun getByAccountId(accountId: AccountId): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE id = :id LIMIT 1")
    suspend fun findById(id: CalendarId): CalendarEntity?

    @Query("SELECT * FROM calendars WHERE id = :calendarId LIMIT 1")
    fun observeCalendar(calendarId: CalendarId): Flow<CalendarEntity?>

    @Update
    suspend fun update(calendar: CalendarEntity)

    @Query("UPDATE calendars SET syncToken = :syncToken WHERE id = :calendarId")
    suspend fun updateSyncToken(calendarId: CalendarId, syncToken: String?)

    @Upsert
    suspend fun upsert(calendars: List<CalendarEntity>)

    @Transaction
    suspend fun syncCalendars(
        accountId: AccountId,
        updater: (Map<CalendarId, CalendarEntity>) -> List<CalendarEntity>,
    ) {
        val existing = getByAccountId(accountId).associateBy { it.id }
        val next = updater(existing)
        upsert(next)
        deleteCalendarsNotExisting(accountId, next.map { it.id })
    }

    @Insert
    suspend fun insert(calendar: CalendarEntity)

    @Query("DELETE FROM calendars WHERE accountId = :accountId AND id NOT IN (:ids)")
    suspend fun deleteCalendarsNotExisting(accountId: AccountId, ids: List<CalendarId>)

}
