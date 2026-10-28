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
package com.infomaniak.multiplatform_core.organisations.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.infomaniak.multiplatform_core.organisations.data.local.entity.OrganisationEntity
import com.infomaniak.multiplatform_core.organisations.domain.model.OrganisationId
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.Flow
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Dao
public interface OrganisationDao {

    @Upsert
    public suspend fun upsertOrganisations(organisations: List<OrganisationEntity>)

    @Query("DELETE FROM organisations WHERE accountId = :accountId")
    public suspend fun deleteOrganisationsForAccount(accountId: AccountId)

    @Transaction
    public suspend fun replaceOrganisations(accountId: AccountId, organisations: List<OrganisationEntity>) {
        deleteOrganisationsForAccount(accountId)
        upsertOrganisations(organisations)
    }

    @Query("SELECT * FROM organisations WHERE accountId = :accountId AND id = :id")
    public suspend fun getOrganisation(accountId: AccountId, id: OrganisationId): OrganisationEntity?

    @Query("SELECT * FROM organisations WHERE accountId = :accountId ORDER BY name ASC")
    public fun observeOrganisations(accountId: AccountId): Flow<List<OrganisationEntity>>
}
