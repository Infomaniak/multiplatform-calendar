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

import com.infomaniak.multiplatform_core.organisations.domain.model.Organisation
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsErrorCause
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrganisationsSyncTest : RobolectricTestsBase() {

    @Test
    fun initAccountDoesNotSync() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val _ = testManager { requests.add(it); respondJson(success("[]")) }

        assertEquals(emptyList(), requests)
    }

    @Test
    fun syncRequestsTheOrganisationsWithTheToken() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("[]")) }

        manager.sync(setOf(ACCOUNT_ID))

        assertEquals("/accounts", requests.single().url.encodedPath)
        assertEquals("Bearer token", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun getOrganisationMapsEveryField() = runTest {
        val manager = testManager { respondJson(success("[${organisationJson()}]")) }

        manager.sync(setOf(ACCOUNT_ID))

        val expected = Organisation(id = ORGANISATION_ID, name = "Team App")
        assertEquals(expected, manager.getOrganisation(ACCOUNT_ID, ORGANISATION_ID))
    }

    @Test
    fun getOrganisationIsScopedToTheAccount() = runTest {
        val manager = testManager { respondJson(success("[${organisationJson()}]")) }
        manager.initAccount(OTHER_ACCOUNT_ID, AccessToken("other"))

        manager.sync(setOf(ACCOUNT_ID))

        assertNull(manager.getOrganisation(OTHER_ACCOUNT_ID, ORGANISATION_ID))
    }

    @Test
    fun syncReplacesOrganisations() = runTest {
        var callCount = 0
        val manager = testManager {
            val name = if (callCount++ == 0) "Team App" else "kDrive Team"
            respondJson(success("[${organisationJson(name = name)}]"))
        }

        manager.sync(setOf(ACCOUNT_ID))
        assertEquals(listOf("Team App"), manager.storedOrganisationNames())
        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(listOf("kDrive Team"), manager.storedOrganisationNames())
    }

    @Test
    fun syncWithoutAccountsSyncsEveryInitializedAccount() = runTest {
        val manager = testManager { respondJson(success("[${organisationJson()}]")) }
        manager.initAccount(OTHER_ACCOUNT_ID, AccessToken("other"))

        manager.sync()

        assertEquals(listOf("Team App"), manager.storedOrganisationNames(ACCOUNT_ID))
        assertEquals(listOf("Team App"), manager.storedOrganisationNames(OTHER_ACCOUNT_ID))
    }

    @Test
    fun failingAccountDoesNotPreventTheOthers() = runTest {
        val manager = testManager { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer token") {
                respondJson("", HttpStatusCode.InternalServerError)
            } else {
                respondJson(success("[${organisationJson()}]"))
            }
        }
        manager.initAccount(OTHER_ACCOUNT_ID, AccessToken("other"))

        assertFailsWithCause(OrganisationsErrorCause.Api(500)) { manager.sync() }

        assertEquals(listOf("Team App"), manager.storedOrganisationNames(OTHER_ACCOUNT_ID))
    }

    @Test
    fun removeAccountClearsOrganisationsAndToken() = runTest {
        val manager = testManager { respondJson(success("[${organisationJson()}]")) }
        manager.sync(setOf(ACCOUNT_ID))

        manager.removeAccount(ACCOUNT_ID)

        assertEquals(emptyList(), manager.storedOrganisationNames())
        assertFailsWithCause(OrganisationsErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun syncWithoutInitAccountThrows() = runTest {
        val manager = testManager(initAccount = false) { respondJson(success("[]")) }

        assertFailsWithCause(OrganisationsErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun unexpectedStatusThrowsApiErrorAndKeepsOrganisations() = runTest {
        var callCount = 0
        val manager = testManager {
            if (callCount++ == 0) respondJson(success("[${organisationJson()}]")) else respondJson("", HttpStatusCode.InternalServerError)
        }
        manager.sync(setOf(ACCOUNT_ID))

        assertFailsWithCause(OrganisationsErrorCause.Api(500)) { manager.sync(setOf(ACCOUNT_ID)) }

        assertEquals(listOf("Team App"), manager.storedOrganisationNames())
    }

    @Test
    fun unreachableServerThrowsNetworkError() = runTest {
        val manager = testManager { throw IOException("offline") }

        assertFailsWithCause(OrganisationsErrorCause.Network) { manager.sync(setOf(ACCOUNT_ID)) }
    }
}
