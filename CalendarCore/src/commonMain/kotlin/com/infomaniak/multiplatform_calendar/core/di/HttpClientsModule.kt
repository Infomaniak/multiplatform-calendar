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
package com.infomaniak.multiplatform_calendar.core.di

import com.infomaniak.multiplatform_core.appenvironment.AppEnvironment
import com.infomaniak.multiplatform_core.contacts.data.remote.createContactsHttpClient
import com.infomaniak.multiplatform_core.network.createHttpClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public enum class Api { Infomaniak, Contacts }

@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class HttpClientFor(val api: Api)

@ContributesTo(AppScope::class)
public interface HttpClientsModule {

    @HttpClientFor(Api.Infomaniak)
    @SingleIn(AppScope::class)
    @Provides
    private fun provideInfomaniakHttpClient(appEnvironment: AppEnvironment): HttpClient =
        createHttpClient(host = "api.${appEnvironment.domain}", basePath = "2/")

    @HttpClientFor(Api.Contacts)
    @SingleIn(AppScope::class)
    @Provides
    private fun provideContactsHttpClient(appEnvironment: AppEnvironment): HttpClient =
        createContactsHttpClient(appEnvironment)
}
