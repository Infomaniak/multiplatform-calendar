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
package com.infomaniak.multiplatform_calendar.bookable_resources.data.remote

import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiAvailabilityRequest
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiBookableResource
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiBusyPeriod
import com.infomaniak.multiplatform_calendar.bookable_resources.data.remote.model.ApiBusyPeriodsRequest
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesErrorCause
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.network.ApiEnvironment
import com.infomaniak.multiplatform_core.network.createHttpClient
import com.infomaniak.multiplatform_core.network.model.asSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

internal fun createBookableResourcesHttpClient(apiEnvironment: ApiEnvironment): HttpClient =
    createHttpClient(host = "calendar.${apiEnvironment.domain}", basePath = "api/")

internal class BookableResourcesRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getBookableResources(token: AccessToken): List<ApiBookableResource> =
        httpClient.get(BookableResourcesRoutes.ALL) { bearerAuth(token.value) }.successOrThrow().asSuccess()

    suspend fun getBookableResource(token: AccessToken, uuid: BookableResourceUuid): ApiBookableResource? {
        val response = httpClient.get(BookableResourcesRoutes.resource(uuid)) { bearerAuth(token.value) }
        return when (response.status) {
            HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> null
            else -> response.successOrThrow().asSuccess()
        }
    }

    suspend fun getAvailability(
        token: AccessToken,
        request: ApiAvailabilityRequest,
        uuid: BookableResourceUuid?,
    ): Map<String, Boolean> {
        val route = uuid?.let(BookableResourcesRoutes::availability) ?: BookableResourcesRoutes.AVAILABILITY
        return httpClient.post(route) { jsonBody(token, request) }
            .successOrThrow()
            .asSuccess()
    }

    suspend fun getBusyPeriods(token: AccessToken, request: ApiBusyPeriodsRequest): Map<String, List<ApiBusyPeriod>> {
        return httpClient.post(BookableResourcesRoutes.BUSY_PERIODS) { jsonBody(token, request) }
            .successOrThrow()
            .asSuccess<Map<String, List<ApiBusyPeriod>>>()
            .mapKeys { (key, _) -> key.removePrefix(BUSY_PERIODS_KEY_PREFIX) }
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(token: AccessToken, body: T) {
        bearerAuth(token.value)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpResponse.successOrThrow(): HttpResponse = apply {
        if (!status.isSuccess()) throw BookableResourcesException(BookableResourcesErrorCause.Api(status.value))
    }

    private companion object {
        const val BUSY_PERIODS_KEY_PREFIX = "bookable#"
    }
}
