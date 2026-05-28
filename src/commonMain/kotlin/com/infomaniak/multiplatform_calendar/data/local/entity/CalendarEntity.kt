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
package com.infomaniak.multiplatform_calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.infomaniak.multiplatform_calendar.data.remote.model.RemoteCalendar
import com.infomaniak.multiplatform_calendar.domain.model.calendar.AccountId
import com.infomaniak.multiplatform_calendar.domain.model.calendar.Color


@Entity(
    tableName = "calendars",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("accountId")]
)
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: AccountId,
    val url: String,
    val displayName: String,
    val color: Color?,
    val caldavColor: Color? = null,
    val isVisible: Boolean = true,
    val ctag: String? = null,
    val readOnly: Boolean = false,
) {
    fun update(remote: RemoteCalendar): CalendarEntity = copy(
        displayName = remote.displayName,
        color = color ?: remote.color,
        caldavColor = remote.color,
        ctag = remote.ctag,
        url = remote.url,
        readOnly = remote.readOnly,
    )
}
