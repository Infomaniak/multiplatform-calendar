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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportFailureTest {

    private val crashReport = RecordingCrashReport()

    @Test
    fun unexpectedFailureIsCaptured() {
        val failure = IllegalStateException("boom")

        crashReport.reportFailure(failure, MESSAGE) { FailureKind.Unexpected }

        assertEquals(listOf(ExceptionCapture(MESSAGE, failure)), crashReport.exceptionCaptures)
    }

    @Test
    fun handledAndNetworkFailuresAreNotCaptured() {
        crashReport.reportFailure(IllegalStateException("handled"), MESSAGE) { FailureKind.Handled }
        crashReport.reportFailure(IllegalStateException("network"), MESSAGE) { FailureKind.Network }

        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    @Test
    fun unexpectedSuppressedFailureIsCaptured() {
        val suppressed = IllegalStateException("boom")
        val failure = IllegalArgumentException("network").apply { addSuppressed(suppressed) }

        crashReport.reportFailure(failure, MESSAGE) { if (it === suppressed) FailureKind.Unexpected else FailureKind.Network }

        assertEquals(listOf(ExceptionCapture(MESSAGE, suppressed)), crashReport.exceptionCaptures)
    }

    private companion object {
        const val MESSAGE = "Call failed"
    }
}
