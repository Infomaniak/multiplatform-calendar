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

import com.infomaniak.multiplatform_core.organisations.data.local.entity.OrganisationEntity
import com.infomaniak.multiplatform_core.organisations.domain.model.OrganisationId
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrganisationDaoTest : RobolectricTestsBase() {

    private val dao = testDatabase().organisationDao()

    @Test
    fun organisationIsRead() = runTest {
        val organisation = organisation()
        assertNull(dao.getOrganisation(ACCOUNT_ID, ORGANISATION_ID))

        dao.upsertOrganisations(listOf(organisation))

        assertEquals(organisation, dao.getOrganisation(ACCOUNT_ID, ORGANISATION_ID))
    }

    @Test
    fun upsertReplacesTheOrganisation() = runTest {
        dao.upsertOrganisations(listOf(organisation(name = "Before")))
        dao.upsertOrganisations(listOf(organisation(name = "After")))

        assertEquals("After", dao.getOrganisation(ACCOUNT_ID, ORGANISATION_ID)?.name)
    }

    @Test
    fun replacingTheOrganisationsOfAnAccountKeepsTheOthers() = runTest {
        dao.upsertOrganisations(listOf(organisation(), organisation(accountId = OTHER_ACCOUNT_ID)))

        dao.replaceOrganisations(ACCOUNT_ID, listOf(organisation(id = OTHER_ORGANISATION_ID)))

        assertNull(dao.getOrganisation(ACCOUNT_ID, ORGANISATION_ID))
        assertEquals(OTHER_ORGANISATION_ID, dao.getOrganisation(ACCOUNT_ID, OTHER_ORGANISATION_ID)?.id)
        assertEquals(ORGANISATION_ID, dao.getOrganisation(OTHER_ACCOUNT_ID, ORGANISATION_ID)?.id)
    }

    private fun organisation(
        id: OrganisationId = ORGANISATION_ID,
        name: String = "Team App",
        accountId: AccountId = ACCOUNT_ID,
    ) = OrganisationEntity(accountId = accountId, id = id, name = name)
}
