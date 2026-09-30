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
package com.infomaniak.multiplatform_core.contacts.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.data.local.entity.ContactAccountEntity
import com.infomaniak.multiplatform_core.contacts.data.local.entity.ContactEntity

@Dao
internal interface ContactDao {

    @Upsert
    suspend fun upsertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE accountId = :accountId")
    suspend fun deleteContactsForAccount(accountId: AccountId)

    @Query(
        """
        SELECT * FROM contacts
        WHERE accountId IN (:accountIds)
        AND (nameNormalized LIKE :likeQuery ESCAPE '\' OR emailNormalized LIKE :likeQuery ESCAPE '\')
        """,
    )
    suspend fun search(accountIds: Set<AccountId>, likeQuery: String): List<ContactEntity>

    @Query("SELECT id FROM accounts")
    suspend fun accountIds(): List<AccountId>

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun account(accountId: AccountId): ContactAccountEntity?

    /** An upsert, not a replace: replacing the account would cascade-delete its contacts. */
    @Upsert
    suspend fun upsertAccount(account: ContactAccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccount(accountId: AccountId)
}
