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
package com.infomaniak.multiplatform_core.contacts

import androidx.room3.useReaderConnection
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.data.local.ContactsDatabase
import com.infomaniak.multiplatform_core.contacts.data.local.ContactsDatabaseSource
import com.infomaniak.multiplatform_core.contacts.data.local.contactsDatabase
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContactsProvider
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import com.infomaniak.multiplatform_core.network.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.job
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal val ACCOUNT_ID = AccountId(42L)

internal class FakeDeviceContactsProvider(initialContacts: List<DeviceContact>?) : DeviceContactsProvider {

    var contacts: List<DeviceContact>? = initialContacts
    var readCount: Int = 0
        private set

    val systemChanges = MutableSharedFlow<Unit>()

    override val changes = systemChanges.onSubscription { emit(Unit) }

    override suspend fun read(): List<DeviceContact>? {
        readCount++
        return contacts
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun testJson(): Json = Json(DefaultJson) {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal fun testHttpClient(
    apiContacts: List<ApiContact>,
    etag: String?,
    onRequest: (HttpRequestData) -> Unit = {},
): HttpClient = HttpClient(MockEngine) {
    install(ContentNegotiation) { json(testJson()) }
    engine {
        addHandler { request ->
            onRequest(request)
            if (request.headers[HttpHeaders.IfNoneMatch] != null) {
                respond("", HttpStatusCode.NotModified, jsonHeaders(etag))
            } else {
                val body = testJson().encodeToString(ApiResponse(result = "success", data = apiContacts))
                respond(body, HttpStatusCode.OK, jsonHeaders(etag))
            }
        }
    }
}

private fun jsonHeaders(etag: String?): Headers = HeadersBuilder().apply {
    append(HttpHeaders.ContentType, "application/json")
    etag?.let { append(HttpHeaders.ETag, it) }
}.build()

internal fun testDatabase(): ContactsDatabase =
    contactsDatabase(ContactsDatabaseSource.InMemory, driver = TestDatabaseFactory.driver())

internal suspend fun testManager(
    apiContacts: List<ApiContact> = emptyList(),
    etag: String? = "\"etag-1\"",
    deviceContacts: List<DeviceContact> = emptyList(),
    initAccount: Boolean = true,
    onRequest: (HttpRequestData) -> Unit = {},
): ContactsManager = ContactsManager(
    database = testDatabase(),
    deviceContactsProvider = FakeDeviceContactsProvider(deviceContacts),
    httpClient = testHttpClient(apiContacts, etag, onRequest),
).apply { if (initAccount) initTestAccount() }

/** Inits the test account and waits for its background sync. */
internal suspend fun ContactsManager.initTestAccount() {
    initAccount(ACCOUNT_ID, AccessToken("token"))
    syncScope.coroutineContext.job.children.forEach { it.join() }
}

/** Names of the contacts stored for [accountId], sorted. */
internal suspend fun ContactsManager.storedContactNames(accountId: AccountId = ACCOUNT_ID): List<String> =
    database.useReaderConnection { connection ->
        connection.usePrepared("SELECT name FROM contacts WHERE accountId = ? ORDER BY name") { statement ->
            statement.bindLong(1, accountId.value)
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
    }

internal suspend fun assertFailsWithCause(expected: ContactsErrorCause, block: suspend () -> Unit) {
    assertEquals(expected, assertFailsWith<ContactsException> { block() }.errorCause)
}
