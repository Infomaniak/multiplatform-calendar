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
package com.infomaniak.multiplatform_core.organisations.data.remote

import com.infomaniak.multiplatform_core.organisations.data.remote.model.ApiOrganisation
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsErrorCause
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.network.ApiEnvironment
import com.infomaniak.multiplatform_core.network.createHttpClient
import com.infomaniak.multiplatform_core.network.model.asSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

internal fun createOrganisationsHttpClient(apiEnvironment: ApiEnvironment): HttpClient =
    createHttpClient(host = "api.${apiEnvironment.domain}", basePath = "1/")

internal class OrganisationsRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getOrganisations(token: AccessToken): List<ApiOrganisation> =
        httpClient.get(OrganisationsRoutes.ALL) { bearerAuth(token.value) }.successOrThrow().asSuccess()

    private fun HttpResponse.successOrThrow(): HttpResponse = apply {
        if (!status.isSuccess()) throw OrganisationsException(OrganisationsErrorCause.Api(status.value))
    }
}
