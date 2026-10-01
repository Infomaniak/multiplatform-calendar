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
package com.infomaniak.multiplatform_core.network.model

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable

/** Envelope of every Infomaniak API response. */
@Serializable
public data class ApiResponse<T>(
    val result: String,
    val data: T? = null,
    val error: ApiError? = null,
) {
    /** Returns [data], or throws an [ApiErrorException] when the call failed. */
    public fun asSuccess(): T = data ?: throw ApiErrorException(this)
}

/** Decodes the [ApiResponse] envelope and returns its data, or throws an [ApiErrorException]. */
public suspend inline fun <reified T> HttpResponse.asSuccess(): T = body<ApiResponse<T>>().asSuccess()
