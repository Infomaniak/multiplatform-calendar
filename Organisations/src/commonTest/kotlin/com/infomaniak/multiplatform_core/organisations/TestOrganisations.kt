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
package com.infomaniak.multiplatform_core.organisations

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.infomaniak.multiplatform_core.organisations.data.local.OrganisationsDatabase
import com.infomaniak.multiplatform_core.organisations.domain.model.OrganisationId
import com.infomaniak.multiplatform_core.account.domain.model.AccountId

internal val ACCOUNT_ID = AccountId(42L)
internal val OTHER_ACCOUNT_ID = AccountId(43L)

internal val ORGANISATION_ID = OrganisationId(678415L)
internal val OTHER_ORGANISATION_ID = OrganisationId(678650L)

internal fun testDatabase(): OrganisationsDatabase =
    Room.inMemoryDatabaseBuilder<TestOrganisationsDatabase>().setDriver(BundledSQLiteDriver()).build()
