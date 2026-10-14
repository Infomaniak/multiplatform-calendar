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
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** A server contact, one row per email address. */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
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
public data class ContactEntity(
    public val accountId: AccountId,
    public val email: String,
    public val name: String,
    public val avatarUrl: String?,
    public val contactedTimes: Int?,
    public val isInAddressBook: Boolean,
    public val nameNormalized: String,
    public val emailNormalized: String,
)
