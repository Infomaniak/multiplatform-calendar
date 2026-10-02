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
package com.infomaniak.multiplatform_core.contacts.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import com.infomaniak.multiplatform_core.account.domain.model.AccountId

/** A server contact, one row per email address. */
@Entity(
    tableName = "contacts",
    primaryKeys = ["accountId", "email", "name"],
    foreignKeys = [
        ForeignKey(
            entity = ContactAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ContactEntity(
    val accountId: AccountId,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val contactedTimes: Int?,
    val other: Boolean,
)
