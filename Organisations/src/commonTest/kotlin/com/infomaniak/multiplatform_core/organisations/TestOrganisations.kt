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

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.infomaniak.multiplatform_core.organisations.data.local.OrganisationsDatabase
import com.infomaniak.multiplatform_core.organisations.domain.model.OrganisationId
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsErrorCause
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal val ACCOUNT_ID = AccountId(42L)
internal val OTHER_ACCOUNT_ID = AccountId(43L)

internal val ORGANISATION_ID = OrganisationId(678415L)
internal val OTHER_ORGANISATION_ID = OrganisationId(678650L)

internal fun organisationJson(
    id: OrganisationId = ORGANISATION_ID,
    name: String = "Team App",
): String = """
    {
      "id": ${id.value},
      "name": "$name",
      "legal_entity_type": "association",
      "created_at": 1657576800,
      "support_level": 1,
      "phone": "+41790000000",
      "type": "normal",
      "billing": false,
      "mailing": false,
      "billing_mailing": false,
      "workspace_only": false,
      "no_access": true,
      "is_blocked": false
    }
""".trimIndent()

internal fun success(data: String): String = """{ "result": "success", "data": $data }"""

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun testHttpClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = HttpClient(MockEngine) {
    install(ContentNegotiation) { json(testJson()) }
    engine { addHandler(handler) }
}

internal suspend fun testManager(
    initAccount: Boolean = true,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): OrganisationsManager = OrganisationsManager(database = testDatabase(), httpClient = testHttpClient(handler))
    .apply { if (initAccount) initAccount(ACCOUNT_ID, AccessToken("token")) }

internal suspend fun OrganisationsManager.storedOrganisationNames(accountId: AccountId = ACCOUNT_ID): List<String> =
    (database as RoomDatabase).useReaderConnection { connection ->
        connection.usePrepared("SELECT name FROM organisations WHERE accountId = ? ORDER BY name") { statement ->
            statement.bindLong(1, accountId.value)
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
    }

@OptIn(ExperimentalSerializationApi::class)
private fun testJson(): Json = Json(DefaultJson) {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal suspend fun assertFailsWithCause(expected: OrganisationsErrorCause, block: suspend () -> Any?) {
    assertEquals(expected, assertFailsWith<OrganisationsException> { block() }.errorCause)
}

internal fun testDatabase(): OrganisationsDatabase =
    Room.inMemoryDatabaseBuilder<TestOrganisationsDatabase>().setDriver(BundledSQLiteDriver()).build()
