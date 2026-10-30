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

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResource
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.BookableResourceUuid
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport

class BookableResourcesObserveResourceTest : RobolectricTestsBase() {

    @Test
    fun missingResourceIsFetched() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success(resourceJson())) }

        val resource = manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).filterNotNull().first()

        assertEquals("Salle American Diner", resource.name)
        assertEquals("/pim/bookable-resources/${RESOURCE_UUID.value}", requests.single().url.encodedPath)
        assertEquals(listOf("Salle American Diner"), manager.storedResourceNames())
    }

    @Test
    fun resourceBeingFetchedIsNotFetchedAgain() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val firstRequest = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        val manager = testManager {
            requests.add(it)
            firstRequest.complete(Unit)
            answer.await()
            respondJson(success(resourceJson()))
        }

        val first = async { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).filterNotNull().first() }
        firstRequest.await()
        val second = async { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).filterNotNull().first() }
        // Leaves the second collector the time to start its own fetch, if it were to.
        withContext(Dispatchers.Default) { delay(200) }
        answer.complete(Unit)

        assertEquals("Salle American Diner", first.await().name)
        assertEquals("Salle American Diner", second.await().name)
        assertEquals(1, requests.size)
    }

    @Test
    fun resourceIsFetchedAgainWhenTheCollectorFetchingItIsCancelled() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val firstRequest = CompletableDeferred<Unit>()
        val manager = testManager {
            requests.add(it)
            if (firstRequest.complete(Unit)) awaitCancellation()
            respondJson(success(resourceJson()))
        }

        val first = launch { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).collect {} }
        firstRequest.await()
        val second = async { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).filterNotNull().first() }
        // Leaves the second collector the time to wait for the first fetch.
        withContext(Dispatchers.Default) { delay(200) }
        first.cancel()

        assertEquals("Salle American Diner", second.await().name)
        assertEquals(2, requests.size)
    }

    @Test
    fun resourceFetchedWhileTheAccountIsRemovedIsNotStored() = runTest {
        val requested = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        val manager = testManager {
            requested.complete(Unit)
            answer.await()
            respondJson(success(resourceJson()))
        }

        val job = launch { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).collect {} }
        requested.await()
        manager.removeAccount(ACCOUNT_ID)
        answer.complete(Unit)
        // Leaves the fetch the time to store the resource, if it were to.
        withContext(Dispatchers.Default) { delay(200) }
        job.cancel()

        assertEquals(emptyList(), manager.storedResourceNames())
    }

    @Test
    fun knownResourceIsNotFetched() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val manager = testManager { requests.add(it); respondJson(success("[${resourceJson()}]")) }
        manager.sync(setOf(ACCOUNT_ID))

        val resource = manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).filterNotNull().first()

        assertEquals("Salle American Diner", resource.name)
        assertEquals(listOf("/pim/bookable-resources/all"), requests.map { it.url.encodedPath })
    }

    @Test
    fun forbiddenResourceStaysNull() = assertResourceStaysNull { respondJson(error("access_denied"), HttpStatusCode.Forbidden) }

    @Test
    fun unknownResourceStaysNull() = assertResourceStaysNull { respondJson(error("object_not_found"), HttpStatusCode.NotFound) }

    @Test
    fun resourceOtherThanMeetingRoomStaysNull() = assertResourceStaysNull {
        respondJson(success(resourceJson(category = "accessory")))
    }

    @Test
    fun fetchFailureStaysNull() = assertResourceStaysNull { throw IOException("offline") }

    @Test
    fun unexpectedFetchFailureIsReported() = runTest {
        val crashReport = RecordingCrashReport()
        val manager = testManager(crashReport = crashReport) { throw IllegalStateException("boom") }

        val job = launch { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).collect {} }
        awaitUntil { crashReport.exceptionCaptures.isNotEmpty() }
        job.cancel()

        val capture = crashReport.exceptionCaptures.single()
        assertEquals("Bookable resource fetch failed", capture.message)
        assertEquals("boom", capture.exception.message)
    }

    @Test
    fun networkFetchFailureIsNotCaptured() = runTest {
        val crashReport = RecordingCrashReport()
        val manager = testManager(crashReport = crashReport) { throw IOException("offline") }

        val job = launch { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).collect {} }
        // Leaves the fetch the time to report the failure, if it were to.
        withContext(Dispatchers.Default) { delay(200) }
        job.cancel()

        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    @Test
    fun uuidIsEncodedInThePath() = runTest {
        val requested = CompletableDeferred<HttpRequestData>()
        val manager = testManager { requested.complete(it); respondJson(error("object_not_found"), HttpStatusCode.NotFound) }

        val job = launch { manager.observeResource(ACCOUNT_ID, BookableResourceUuid("../all")).collect {} }
        val request = requested.await()
        job.cancel()

        assertEquals("/pim/bookable-resources/..%2Fall", request.url.encodedPath)
    }

    private fun assertResourceStaysNull(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = runTest {
        val answered = CompletableDeferred<Unit>()
        val manager = testManager { request ->
            try {
                response(request)
            } finally {
                answered.complete(Unit)
            }
        }
        val emissions = Channel<BookableResource?>(Channel.UNLIMITED)

        val job = launch { manager.observeResource(ACCOUNT_ID, RESOURCE_UUID).collect(emissions::send) }
        assertNull(emissions.receive())
        answered.await()
        job.cancel()

        assertNull(emissions.tryReceive().getOrNull())
        assertEquals(emptyList(), manager.storedResourceNames())
    }

    private suspend fun awaitUntil(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { while (!condition()) delay(10) }
    }

    private fun error(code: String) = """{ "result": "error", "error": { "code": "$code", "description": "" } }"""
}
