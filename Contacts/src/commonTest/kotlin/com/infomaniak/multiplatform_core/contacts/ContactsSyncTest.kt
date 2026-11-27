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

import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import com.infomaniak.multiplatform_core.appenvironment.AppEnvironment
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.crashreport.NoOpCrashReport
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
    fun initAccountDoesNotSync() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
            initAccount = false,
            onRequest = { requests.add(it) },
        )

        manager.initTestAccount()

        assertEquals(emptyList(), requests)
        assertEquals(emptyList(), manager.storedContactNames())
    }

    @Test
    fun syncRequestsTheAccountContactsWithEmails() = runTest {
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
    fun syncMergesComplementaryDuplicatesWhateverTheirOrder() = runTest {
        val duplicates = listOf(
            ApiContact(name = "John", emails = listOf("john@x.com"), avatar = "/avatar/john.png", other = true),
            ApiContact(name = "John", emails = listOf("john@x.com"), contactedTimes = mapOf("john@x.com" to 3)),
            ApiContact(name = "John", emails = listOf("john@x.com"), other = true, contactedTimes = mapOf("john@x.com" to 5)),
        )

        duplicates.permutations().forEach { apiContacts ->
            assertEquals(
                listOf(StoredContact("john@x.com", "https://mail.infomaniak.com/avatar/john.png", 5, true)),
                testManager(apiContacts = apiContacts).storedContacts(),
            )
        }
    }

    @Test
    fun syncMergesDuplicatesWhoseEmailsDifferOnlyByCase() = runTest {
        val duplicates = listOf(
            ApiContact(name = "John", emails = listOf("john@x.com"), contactedTimes = mapOf("john@x.com" to 3)),
            ApiContact(name = "John", emails = listOf("John@x.com"), contactedTimes = mapOf("John@x.com" to 5)),
        )

        duplicates.permutations().forEach { apiContacts ->
            val manager = testManager(apiContacts = apiContacts)

            assertEquals(listOf(StoredContact("John@x.com", null, 5, true)), manager.storedContacts())
        }
    }

    @Test
    fun syncKeepsTheAvatarOfAnAddressBookThenTheSmallestUrl() = runTest {
        val duplicates = listOf(
            ApiContact(name = "John", emails = listOf("john@x.com"), avatar = "/avatar/a.png", other = true),
            ApiContact(name = "John", emails = listOf("john@x.com"), avatar = "/avatar/c.png"),
            ApiContact(name = "John", emails = listOf("john@x.com"), avatar = "/avatar/b.png"),
        )

        duplicates.permutations().forEach { apiContacts ->
            assertEquals(
                listOf("https://mail.infomaniak.com/avatar/b.png"),
                testManager(apiContacts = apiContacts).storedContacts().map { it.avatarUrl },
            )
        }
    }

    @Test
    fun syncKeepsAnUnknownContactedTimesWhenNoDuplicateKnowsIt() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "John", emails = listOf("john@x.com")),
                ApiContact(name = "John", emails = listOf("john@x.com"), other = true),
            ),
        )

        assertEquals(listOf(StoredContact("john@x.com", null, null, true)), manager.storedContacts())
    }

    @Test
    fun syncRequestsEveryContactOnceTheEtagIsForgotten() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
            onRequest = { requests.add(it) },
        )
        (manager.database as RoomDatabase).useWriterConnection { connection ->
            connection.usePrepared("UPDATE contactAccounts SET etag = NULL") { it.step() }
        }

        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(2, requests.size)
        assertNull(requests.last().headers[HttpHeaders.IfNoneMatch])
        assertEquals(listOf("John"), manager.storedContactNames())
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
            appEnvironment = AppEnvironment.Prod,
            deviceContactsProvider = FakeDeviceContactsProvider(emptyList()),
            crashReport = NoOpCrashReport,
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

        manager.sync(setOf(ACCOUNT_ID))
        assertEquals(listOf("John"), manager.storedContactNames())
        manager.sync(setOf(ACCOUNT_ID))

        assertEquals(listOf("Jane"), manager.storedContactNames())
    }

    @Test
    fun removeAccountClearsLocalDataAndToken() = runTest {
        val database = testDatabase()
        val manager = ContactsManager(
            database = database,
            appEnvironment = AppEnvironment.Prod,
            deviceContactsProvider = FakeDeviceContactsProvider(emptyList()),
            crashReport = NoOpCrashReport,
            httpClient = testHttpClient(
                apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
                etag = null,
            ),
        ).apply { initTestAccount() }

        manager.sync(setOf(ACCOUNT_ID))
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
        manager.sync(setOf(ACCOUNT_ID))

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
            appEnvironment = AppEnvironment.Prod,
            deviceContactsProvider = FakeDeviceContactsProvider(listOf(DeviceContact(email = "john@x.com", name = "John"))),
            crashReport = NoOpCrashReport,
            httpClient = HttpClient(MockEngine) { engine { addHandler { throw IOException("offline") } } },
        ).apply { initTestAccount() }

        assertEquals(listOf("John"), manager.search("john", setOf(ACCOUNT_ID)).map { it.name })
    }

    private suspend fun managerWithHandler(handler: MockRequestHandler) = ContactsManager(
        database = testDatabase(),
        appEnvironment = AppEnvironment.Prod,
        deviceContactsProvider = FakeDeviceContactsProvider(emptyList()),
        crashReport = NoOpCrashReport,
        httpClient = HttpClient(MockEngine) { engine { addHandler(handler) } },
    ).apply { initTestAccount() }

    private data class StoredContact(val email: String, val avatarUrl: String?, val contactedTimes: Int?, val isInAddressBook: Boolean)

    private suspend fun ContactsManager.storedContacts(): List<StoredContact> =
        (database as RoomDatabase).useReaderConnection { connection ->
            connection.usePrepared("SELECT email, avatarUrl, contactedTimes, isInAddressBook FROM contacts") { statement ->
                buildList {
                    while (statement.step()) {
                        add(
                            StoredContact(
                                email = statement.getText(0),
                                avatarUrl = if (statement.isNull(1)) null else statement.getText(1),
                                contactedTimes = if (statement.isNull(2)) null else statement.getInt(2),
                                isInAddressBook = statement.getBoolean(3),
                            ),
                        )
                    }
                }
            }
        }

    private fun <T> List<T>.permutations(): List<List<T>> =
        if (size <= 1) listOf(this) else flatMap { element -> (this - element).permutations().map { listOf(element) + it } }
}
