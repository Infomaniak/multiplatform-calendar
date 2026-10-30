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
package com.infomaniak.multiplatform_core.crashreport

import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport.ExceptionCapture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModuleCallerTest {

    private val crashReport = RecordingCrashReport()
    private val caller = TestCaller(crashReport)

    @Test
    fun resultIsReturned() = runTest {
        assertEquals(42, caller { 42 })
    }

    @Test
    fun failureIsReportedAndConverted() = runTest {
        val failure = IllegalStateException("boom")

        assertSame(failure, assertFailsWith<TestException> { caller { throw failure } }.cause)
        assertEquals(listOf(ExceptionCapture(MESSAGE, failure)), crashReport.exceptionCaptures)
    }

    @Test
    fun cancellationIsNotReported() = runTest {
        assertFailsWith<CancellationException> { caller { throw CancellationException("cancelled") } }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    private class TestException(cause: Exception) : Exception(cause), ModuleHandledException

    private class TestCaller(crashReport: CrashReport) : ModuleCaller<TestException>(crashReport, MESSAGE) {
        override fun kindOf(failure: Throwable) = FailureKind.Unexpected
        override fun convert(failure: Exception) = TestException(failure)
    }

    private companion object {
        const val MESSAGE = "Call failed"
    }
}
