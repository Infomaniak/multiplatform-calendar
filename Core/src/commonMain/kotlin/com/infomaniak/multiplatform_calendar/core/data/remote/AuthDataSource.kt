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
package com.infomaniak.multiplatform_calendar.core.data.remote

import com.infomaniak.multiplatform_calendar.core.data.remote.model.PasswordResponse
import com.infomaniak.multiplatform_calendar.core.data.remote.model.asSuccess
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents.Formats.RFC_1123
import kotlin.time.Clock.System.now
import kotlin.time.ExperimentalTime

@SingleIn(AppScope::class)
@Inject
class AuthDataSource(
    private val ktorClient: HttpClient,
) {
    @OptIn(ExperimentalTime::class)
    suspend fun exchangeTokenToPassword(authToken: String): PasswordResponse {
        return ktorClient.post("profile/password") {
            bearerAuth(authToken)
            setBody(
                MultiPartFormDataContent(formData { append("name", "calendar - ${now().format(RFC_1123)}") }),
            )
        }.asSuccess()
    }
}
