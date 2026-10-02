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

import com.infomaniak.multiplatform_core.contacts.data.local.contactsDatabase
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.network.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactsSyncTest : RobolectricTestsBase() {

    @Test
    fun initAccountSyncsInBackground() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
            onRequest = { requests.add(it) },
        )

        assertEquals(listOf("John"), manager.storedContactNames())

        assertEquals(1, requests.size)
        assertEquals("Bearer token", requests.single().headers[HttpHeaders.Authorization])
        assertEquals("emails,details,others,contacted_times", requests.single().url.parameters["with"])
        assertEquals("has_email", requests.single().url.parameters["filters"])
    }

    @Test
    fun syncSendsEtagAndKeepsDataOnNotModified() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
            etag = "\"etag-1\"",
            onRequest = { requests.add(it) },
        )

        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(2, requests.size)
        assertEquals("\"etag-1\"", requests.last().headers[HttpHeaders.IfNoneMatch])

        assertEquals(listOf("John"), manager.storedContactNames())
        assertEquals(2, requests.size)
    }

    @Test
    fun syncWithSuccessReplacesContacts() = runTest {
        var callCount = 0
        val firstContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com")))
        val secondContacts = listOf(ApiContact(name = "Jane", emails = listOf("jane@x.com")))
        val manager = ContactsManager(
            database = testDatabase(),
            deviceContactsProvider = FakeDeviceContactsProvider(emptyList()),
            httpClient = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(testJson()) }
                engine {
                    addHandler { _ ->
                        val contacts = if (callCount++ == 0) firstContacts else secondContacts
                        val body = testJson().encodeToString(ApiResponse(result = "success", data = contacts))
                        respond(
                            content = body,
                            status = HttpStatusCode.OK,
                            headers = HeadersBuilder().apply {
                                append(HttpHeaders.ETag, "\"etag-$callCount\"")
                                append(HttpHeaders.ContentType, "application/json")
                            }.build(),
                        )
                    }
                }
            },
        ).apply { initTestAccount() }

        assertEquals(listOf("John"), manager.storedContactNames())
        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(listOf("Jane"), manager.storedContactNames())
    }

    @Test
    fun removeAccountClearsLocalDataAndToken() = runTest {
        val database = testDatabase()
        val manager = ContactsManager(
            database = database,
            deviceContactsProvider = FakeDeviceContactsProvider(emptyList()),
            httpClient = testHttpClient(
                apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
                etag = null,
            ),
        ).apply { initTestAccount() }

        assertEquals(listOf("John"), manager.storedContactNames())
        manager.removeAccount(ACCOUNT_ID)

        assertEquals(emptyList(), manager.storedContactNames())
        assertNull(database.contactDao().account(ACCOUNT_ID))
        assertFailsWithCause(ContactsErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun syncWithoutInitAccountThrows() = runTest {
        val manager = testManager(initAccount = false)

        assertFailsWithCause(ContactsErrorCause.AccountNotInitialized(ACCOUNT_ID)) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun initAccountEnablesSync() = runTest {
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
            initAccount = false,
        )

        assertEquals(emptyList(), manager.storedContactNames())

        manager.initTestAccount()

        assertEquals(listOf("John"), manager.storedContactNames())
    }

    @Test
    fun unexpectedStatusThrowsApiError() = runTest {
        val manager = managerWithHandler { respond("", HttpStatusCode.InternalServerError) }

        assertFailsWithCause(ContactsErrorCause.Api(500)) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun unreachableServerThrowsNetworkError() = runTest {
        val manager = managerWithHandler { throw IOException("offline") }

        assertFailsWithCause(ContactsErrorCause.Network) { manager.sync(setOf(ACCOUNT_ID)) }
    }

    @Test
    fun searchStillReturnsDeviceContactsWhenOffline() = runTest {
        val manager = ContactsManager(
            database = testDatabase(),
            deviceContactsProvider = FakeDeviceContactsProvider(listOf(DeviceContact(email = "john@x.com", name = "John"))),
            httpClient = HttpClient(MockEngine) { engine { addHandler { throw IOException("offline") } } },
        ).apply { initTestAccount() }

        assertEquals(listOf("John"), manager.search("john", setOf(ACCOUNT_ID)).map { it.name })
    }

    private suspend fun managerWithHandler(handler: MockRequestHandler) = ContactsManager(
        database = testDatabase(),
        deviceContactsProvider = FakeDeviceContactsProvider(emptyList()),
        httpClient = HttpClient(MockEngine) { engine { addHandler(handler) } },
    ).apply { initTestAccount() }
}
