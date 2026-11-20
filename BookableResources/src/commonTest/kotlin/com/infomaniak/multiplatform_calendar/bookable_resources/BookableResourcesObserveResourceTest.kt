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
package com.infomaniak.multiplatform_calendar.bookable_resources

import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResource
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookableResourcesObserveResourceTest : RobolectricTestsBase() {

    @Test
    fun missingResourceIsEmittedOnceSynced() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("[${resourceJson()}]")) }
        val emissions = Channel<BookableResource?>(Channel.UNLIMITED)

        val job = launch { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).collect(emissions::send) }
        assertNull(emissions.receive())
        assertEquals(emptyList(), requests)

        manager.sync(setOf(ACCOUNT_ID))

        assertEquals("Salle American Diner", emissions.receive()?.name)
        job.cancel()
    }
}
