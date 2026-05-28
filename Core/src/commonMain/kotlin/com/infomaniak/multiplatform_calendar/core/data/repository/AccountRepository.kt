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
package com.infomaniak.multiplatform_calendar.data.repository

import com.infomaniak.multiplatform_calendar.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.data.local.entity.AccountEntity
import com.infomaniak.multiplatform_calendar.data.remote.model.CaldavCredentials
import com.infomaniak.multiplatform_calendar.di.AppScope
import com.infomaniak.multiplatform_calendar.domain.model.calendar.AccountId
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class AccountRepository(
    private val accountDao: AccountDao
) {

    private val userCredentials: HashMap<AccountId, CaldavCredentials> = HashMap()

    fun getCredentials(accountId: AccountId): CaldavCredentials? {
        return userCredentials[accountId]
    }

    suspend fun storeCredentials(accountId: AccountId, credentials: CaldavCredentials) {
        userCredentials[accountId] = credentials
        accountDao.insert(AccountEntity(id = accountId))
    }

    suspend fun removeCredentials(accountId: AccountId) {
        userCredentials.remove(accountId)
        accountDao.delete(accountId)
    }

}
