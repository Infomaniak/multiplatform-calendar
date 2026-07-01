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
package com.infomaniak.multiplatform_calendar.core.repository

import com.infomaniak.multiplatform_calendar.core.RobolectricTestsBase
import com.infomaniak.multiplatform_calendar.core.data.local.CalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.local.dao.AccountDao
import com.infomaniak.multiplatform_calendar.core.data.local.getCalendarDatabase
import com.infomaniak.multiplatform_calendar.core.data.remote.AuthDataSource
import com.infomaniak.multiplatform_calendar.core.data.repository.AccountRepository
import com.infomaniak.multiplatform_calendar.core.domain.model.account.AccountId
import com.infomaniak.multiplatform_calendar.core.domain.model.account.DavCredentials
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.utils.DatabaseProviderFactory
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AccountRepositoryTest : RobolectricTestsBase() {

    private lateinit var database: CalendarDatabase
    private lateinit var accountDao: AccountDao

    @BeforeTest
    fun setUp() {
        val databaseConfig = DatabaseProviderFactory.createTestDatabaseConfig()
        database = databaseConfig.getCalendarDatabase(driver = DatabaseProviderFactory.driver(), inMemory = true)
        accountDao = database.accountDao()
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun storeCredentials_savesCredentials_persistsAccount_andEmitsCurrentIds() = runTest {
        val repository = createRepositoryWithLocalAuthCalls()
        val accountId = AccountId(10)
        val credentials = DavAccount(baseUrl = "https://dav.example", username = "john", password = "secret")

        repository.storeCredentials(accountId, credentials)

        assertEquals(credentials, repository.getCredentials(accountId))
        assertEquals(setOf(accountId), repository.currentAccountIdsFlow.first())
    }

    @Test
    fun getCredentials_throwsForUnknownAccount() = runTest {
        val repository = createRepositoryWithLocalAuthCalls()
        val accountId = AccountId(999)
        val exception = assertFailsWith<IllegalStateException> {
            repository.getCredentials(accountId)
        }
        assertEquals("Credentials for account $accountId not found", exception.message)
    }

    @Test
    fun getCredentials_returnsStoredCredentialsForExistingAccount() = runTest {
        val repository = createRepositoryWithLocalAuthCalls()
        val accountId = AccountId(13)
        val credentials = DavAccount(baseUrl = "https://dav.example", username = "existing", password = "secret")

        repository.storeCredentials(accountId, credentials)

        assertEquals(credentials, repository.getCredentials(accountId))
    }

    @Test
    fun removeCredentials_removesCredentials_thenGetCredentialsThrows_andEmitsUpdatedIds() = runTest {
        val repository = createRepositoryWithLocalAuthCalls()
        val accountId = AccountId(11)
        val credentials = DavAccount(baseUrl = "https://dav.example", username = "john", password = "secret")

        repository.storeCredentials(accountId, credentials)
        repository.removeCredentials(accountId)

        val exception = assertFailsWith<IllegalStateException> {
            repository.getCredentials(accountId)
        }
        assertEquals("Credentials for account $accountId not found", exception.message)
        assertEquals(emptySet(), repository.currentAccountIdsFlow.first())
    }

    @Test
    fun storeCredentials_sameAccountAndCredentials_isNoOp() = runTest {
        val repository = createRepositoryWithLocalAuthCalls()
        val accountId = AccountId(12)
        val credentials = DavAccount(baseUrl = "https://dav.example", username = "john", password = "secret")

        repository.storeCredentials(accountId, credentials)
        repository.storeCredentials(accountId, credentials)

        assertEquals(setOf(accountId), repository.currentAccountIdsFlow.first())
    }

    @Test
    fun retrieveDavCredential_withExplicitLogin_usesLocalPasswordCallOnly() = runTest {
        val calledPaths = mutableListOf<String>()
        val repository = createRepositoryWithLocalAuthCalls(calledPaths = calledPaths)

        val credentials = repository.retrieveDavCredential(
            authToken = "token-1",
            login = "provided_login",
        )

        assertEquals(DavCredentials(username = "provided_login", password = "generated-password"), credentials)
        assertEquals(listOf("profile/password"), calledPaths)
    }

    @Test
    fun retrieveDavCredential_withoutLogin_fetchesProfileAndPasswordLocally() = runTest {
        val calledPaths = mutableListOf<String>()
        val repository = createRepositoryWithLocalAuthCalls(calledPaths = calledPaths)

        val credentials = repository.retrieveDavCredential(authToken = "token-2")

        assertEquals(DavCredentials(username = "api_login", password = "generated-password"), credentials)
        assertEquals(listOf("profile", "profile/password"), calledPaths)
    }

    @Test
    fun retrieveDavCredential_wrapsNonCancellationExceptions() = runTest {
        val repository = createRepositoryWithLocalAuthCalls { _, _ ->
            throw IllegalStateException("boom")
        }

        val exception = assertFailsWith<CalendarSdkException> {
            repository.retrieveDavCredential(authToken = "token-3")
        }

        assertIs<IllegalStateException>(exception.cause)
    }

    @Test
    fun retrieveDavCredential_rethrowsCancellationException() = runTest {
        val repository = createRepositoryWithLocalAuthCalls { _, _ ->
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            repository.retrieveDavCredential(authToken = "token-4")
        }
    }

    @Test
    fun storeCredentials_concurrentCalls_keepUserCredentialsThreadSafe() = runTest {
        val repository = createRepositoryWithLocalAuthCalls()
        val accounts = (1L..40L).map { index ->
            AccountId(index) to DavAccount(
                baseUrl = "https://dav-$index.example",
                username = "user-$index",
                password = "password-$index",
            )
        }

        coroutineScope {
            accounts.map { (accountId, credentials) ->
                async {
                    repository.storeCredentials(accountId, credentials)
                }
            }.awaitAll()
        }

        val currentIds = repository.currentAccountIdsFlow.first()
        assertEquals(accounts.map { it.first }.toSet(), currentIds)
        accounts.forEach { (accountId, credentials) ->
            assertEquals(credentials, repository.getCredentials(accountId))
        }
    }

    private fun createRepositoryWithLocalAuthCalls(
        calledPaths: MutableList<String>? = null,
        responder: ((path: String, request: HttpRequestData) -> HttpResponseData)? = null,
    ): AccountRepository {
        val authDataSource = AuthDataSource(createMockHttpClient(calledPaths, responder))
        return AccountRepository(accountDao = accountDao, authDataSource = authDataSource)
    }

    private fun createMockHttpClient(
        calledPaths: MutableList<String>? = null,
        responder: ((path: String, request: HttpRequestData) -> HttpResponseData)? = null,
    ): HttpClient {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath.trimStart('/')
            calledPaths?.add(path)
            responder?.invoke(path, request) ?: defaultSuccessResponse(path)
        }

        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun MockRequestHandleScope.defaultSuccessResponse(path: String): HttpResponseData {
        val responseBody = when (path) {
            "profile" -> """
                {"result":"success","data":{"login":"api_login","email":"api@example.com","displayName":"Api User"},"error":null}
            """.trimIndent()

            "profile/password" -> """
                {"result":"success","data":{"id":1,"name":"calendar token","password":"generated-password"},"error":null}
            """.trimIndent()

            else -> error("Unexpected local API path: $path")
        }

        return respond(
            content = responseBody,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
}

