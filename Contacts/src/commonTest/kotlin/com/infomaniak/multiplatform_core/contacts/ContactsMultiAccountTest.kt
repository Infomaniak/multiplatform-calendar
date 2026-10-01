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

import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.network.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactsMultiAccountTest : RobolectricTestsBase() {

    private val contactsByToken = mutableMapOf<String, List<ApiContact>>()

    @Test
    fun searchWithoutAccountsSearchesEverySyncedAccount() = runTest {
        contactsByToken[TOKEN_1] = listOf(ApiContact(name = "John", emails = listOf("john@x.com")))
        contactsByToken[TOKEN_2] = listOf(ApiContact(name = "Jane", emails = listOf("jane@x.com")))
        val manager = managerWithBothAccounts()

        assertEquals(listOf("Jane", "John"), manager.search("j").map { it.name })
        assertEquals(listOf("John"), manager.search("j", setOf(ACCOUNT_1)).map { it.name })
    }

    @Test
    fun contactFoundInSeveralAccountsIsReturnedOnceWithContactedTimesAddedUp() = runTest {
        contactsByToken[TOKEN_1] = listOf(
            ApiContact(name = "Bob", emails = listOf("bob@x.com"), contactedTimes = mapOf("bob@x.com" to 1)),
            ApiContact(name = "Bea", emails = listOf("bea@x.com"), contactedTimes = mapOf("bea@x.com" to 2)),
        )
        contactsByToken[TOKEN_2] = listOf(
            ApiContact(name = "Bob", emails = listOf("bob@x.com"), contactedTimes = mapOf("bob@x.com" to 2)),
        )
        val manager = managerWithBothAccounts()

        assertEquals(listOf("Bob", "Bea"), manager.search("b").map { it.name })
    }

    @Test
    fun syncWithoutAccountsSyncsEveryInitializedAccount() = runTest {
        val manager = managerWithBothAccounts()
        contactsByToken[TOKEN_1] = listOf(ApiContact(name = "John", emails = listOf("john@x.com")))
        contactsByToken[TOKEN_2] = listOf(ApiContact(name = "Jane", emails = listOf("jane@x.com")))

        manager.sync()

        assertEquals(listOf("John"), manager.storedContactNames(ACCOUNT_1))
        assertEquals(listOf("Jane"), manager.storedContactNames(ACCOUNT_2))
    }

    @Test
    fun failingAccountDoesNotPreventTheOthersFromSyncing() = runTest {
        val manager = managerWithBothAccounts()
        contactsByToken[TOKEN_1] = listOf(ApiContact(name = "John", emails = listOf("john@x.com")))
        contactsByToken.remove(TOKEN_2)

        assertFailsWithCause(ContactsErrorCause.Api(500)) { manager.sync() }
        assertEquals(listOf("John"), manager.storedContactNames(ACCOUNT_1))
    }

    @Test
    fun syncOfAnUninitializedAccountThrowsAfterSyncingTheOthers() = runTest {
        val manager = managerWithBothAccounts()
        contactsByToken[TOKEN_1] = listOf(ApiContact(name = "John", emails = listOf("john@x.com")))
        val unknownAccount = AccountId(3L)

        assertFailsWithCause(ContactsErrorCause.AccountNotInitialized(unknownAccount)) {
            manager.sync(setOf(ACCOUNT_1, unknownAccount))
        }
        assertEquals(listOf("John"), manager.storedContactNames(ACCOUNT_1))
    }

    /** Inits both accounts and waits for their background syncs. */
    private suspend fun managerWithBothAccounts() = ContactsManager(database = testDatabase(), httpClient = httpClient()).apply {
        initAccount(ACCOUNT_1, AccessToken(TOKEN_1))
        initAccount(ACCOUNT_2, AccessToken(TOKEN_2))
        syncScope.coroutineContext.job.children.forEach { it.join() }
    }

    /** Answers with the contacts of the request token, or a server error for an unknown token. */
    private fun httpClient() = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(testJson()) }
        engine {
            addHandler { request ->
                val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                val contacts = contactsByToken[token] ?: return@addHandler respond("", HttpStatusCode.InternalServerError)
                val body = testJson().encodeToString(ApiResponse(result = "success", data = contacts))
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    }

    private companion object {
        val ACCOUNT_1 = AccountId(1L)
        val ACCOUNT_2 = AccountId(2L)
        const val TOKEN_1 = "token-1"
        const val TOKEN_2 = "token-2"
    }
}
