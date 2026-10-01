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
package com.infomaniak.multiplatform_core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Creates an [HttpClient] for an Infomaniak API reached at `https://[host]/[basePath]`, exchanging snake_case JSON.
 * The engine is the platform one: OkHttp on Android, Darwin on Apple.
 */
@OptIn(ExperimentalSerializationApi::class)
public fun createHttpClient(host: String, basePath: String): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(
            Json(DefaultJson) {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            },
        )
    }
    defaultRequest {
        url {
            protocol = URLProtocol.HTTPS
            this.host = host
            path(basePath)
        }
    }
}
