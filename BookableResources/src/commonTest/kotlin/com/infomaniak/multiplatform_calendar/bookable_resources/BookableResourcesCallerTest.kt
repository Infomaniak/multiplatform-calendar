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

import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesErrorCause
import com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions.BookableResourcesException
import com.infomaniak.multiplatform_calendar.bookable_resources.utils.BookableResourcesCaller
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport.ExceptionCapture
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BookableResourcesCallerTest {

    private val crashReport = RecordingCrashReport()
    private val bookableResourcesCaller = BookableResourcesCaller(crashReport)

    @Test
    fun unexpectedFailureIsReportedAndWrapped() = runTest {
        val failure = IllegalStateException("boom")

        assertCallerFailsWithCause(BookableResourcesErrorCause.Unknown(failure)) { throw failure }
        assertEquals(listOf(ExceptionCapture("Bookable resources call failed", failure)), crashReport.exceptionCaptures)
    }

    @Test
    fun networkFailureIsNotCaptured() = runTest {
        assertCallerFailsWithCause(BookableResourcesErrorCause.Network) { throw IOException("offline") }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    @Test
    fun bookableResourcesFailureIsNotCaptured() = runTest {
        val cause = BookableResourcesErrorCause.AccountNotInitialized(ACCOUNT_ID)

        assertCallerFailsWithCause(cause) { throw BookableResourcesException(cause) }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    private suspend fun assertCallerFailsWithCause(expected: BookableResourcesErrorCause, block: suspend () -> Unit) {
        assertEquals(expected, assertFailsWith<BookableResourcesException> { bookableResourcesCaller { block() } }.errorCause)
    }
}
