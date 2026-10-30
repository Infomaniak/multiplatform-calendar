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
package com.infomaniak.multiplatform_calendar.meet

import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetErrorCause
import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetException
import com.infomaniak.multiplatform_calendar.meet.utils.MeetCaller
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport.ExceptionCapture
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MeetCallerTest {

    private val crashReport = RecordingCrashReport()
    private val meetCaller = MeetCaller(crashReport)

    @Test
    fun unexpectedFailureIsReportedAndWrapped() = runTest {
        val failure = IllegalStateException("boom")

        assertFailsWithCause(MeetErrorCause.Unknown(failure)) { throw failure }
        assertEquals(listOf(ExceptionCapture("Meet call failed", failure)), crashReport.exceptionCaptures)
    }

    @Test
    fun networkFailureIsNotCaptured() = runTest {
        assertFailsWithCause(MeetErrorCause.Network) { throw IOException("offline") }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    @Test
    fun meetFailureIsNotCaptured() = runTest {
        val cause = MeetErrorCause.AccountNotInitialized(AccountId(42L))

        assertFailsWithCause(cause) { throw MeetException(cause) }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    private suspend fun assertFailsWithCause(expected: MeetErrorCause, block: suspend () -> Unit) {
        assertEquals(expected, assertFailsWith<MeetException> { meetCaller { block() } }.errorCause)
    }
}
