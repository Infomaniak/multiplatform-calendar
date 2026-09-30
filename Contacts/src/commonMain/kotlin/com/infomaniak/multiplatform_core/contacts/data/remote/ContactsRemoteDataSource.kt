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
package com.infomaniak.multiplatform_core.contacts.data.remote

import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import com.infomaniak.multiplatform_core.network.createHttpClient
import com.infomaniak.multiplatform_core.network.model.asSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

internal const val MAIL_API_HOST = "mail.infomaniak.com"

internal fun createContactsHttpClient(): HttpClient = createHttpClient(host = MAIL_API_HOST, basePath = "api/")

internal class ContactsRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getContacts(token: AccessToken, etag: String?): ContactsFetch {
        val response: HttpResponse = httpClient.get(ContactsRoutes.CONTACTS) {
            bearerAuth(token.value)
            parameter("with", "emails,details,others,contacted_times")
            parameter("filters", "has_email")
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        return when {
            response.status == HttpStatusCode.NotModified -> ContactsFetch.NotModified
            response.status.isSuccess() -> ContactsFetch.Success(
                contacts = response.asSuccess<List<ApiContact>>(),
                etag = response.headers[HttpHeaders.ETag],
            )
            else -> throw ContactsException(ContactsErrorCause.Api(response.status.value))
        }
    }
}

private object ContactsRoutes {
    const val CONTACTS = "pim/contact/all"
}
