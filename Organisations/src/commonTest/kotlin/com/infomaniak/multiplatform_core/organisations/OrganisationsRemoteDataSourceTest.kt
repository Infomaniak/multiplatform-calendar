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

import com.infomaniak.multiplatform_core.organisations.data.remote.OrganisationsRemoteDataSource
import com.infomaniak.multiplatform_core.organisations.data.remote.model.ApiOrganisation
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsErrorCause
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OrganisationsRemoteDataSourceTest {

    private val token = AccessToken("token")

    @Test
    fun organisationsAreDecoded() = runTest {
        val dataSource = dataSource { respondJson(success("[${organisationJson()}]")) }

        val expected = ApiOrganisation(id = ORGANISATION_ID.value, name = "Team App", type = "normal")
        assertEquals(listOf(expected), dataSource.getOrganisations(token))
    }

    @Test
    fun serverErrorThrowsApiError() = runTest {
        val dataSource = dataSource { respondJson("", HttpStatusCode.InternalServerError) }

        assertFailsWithCause(OrganisationsErrorCause.Api(500)) { dataSource.getOrganisations(token) }
    }

    private fun dataSource(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = OrganisationsRemoteDataSource(testHttpClient(response))
}
