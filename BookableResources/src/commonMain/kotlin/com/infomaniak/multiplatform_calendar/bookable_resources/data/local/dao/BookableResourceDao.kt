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
package com.infomaniak.multiplatform_calendar.bookable_resources.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.infomaniak.multiplatform_calendar.bookable_resources.data.local.entity.BookableResourceEntity
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.flow.Flow
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Dao
public interface BookableResourceDao {

    @Upsert
    public suspend fun upsertResources(resources: List<BookableResourceEntity>)

    @Query("DELETE FROM bookableResources WHERE accountId = :accountId")
    public suspend fun deleteResourcesForAccount(accountId: AccountId)

    @Transaction
    public suspend fun replaceResources(accountId: AccountId, resources: List<BookableResourceEntity>) {
        deleteResourcesForAccount(accountId)
        upsertResources(resources)
    }

    @Query("SELECT * FROM bookableResources WHERE accountId = :accountId ORDER BY name COLLATE NOCASE")
    public fun observeResources(accountId: AccountId): Flow<List<BookableResourceEntity>>

    @Query("SELECT * FROM bookableResources WHERE accountId = :accountId AND uuid = :uuid")
    public fun observeResource(accountId: AccountId, uuid: BookableResourceUuid): Flow<BookableResourceEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM bookableResources WHERE accountId = :accountId AND uuid = :uuid)")
    public suspend fun hasResource(accountId: AccountId, uuid: BookableResourceUuid): Boolean
}
