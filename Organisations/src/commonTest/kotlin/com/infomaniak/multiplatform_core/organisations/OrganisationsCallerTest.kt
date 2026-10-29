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
package com.infomaniak.multiplatform_core.organisations

import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport.ExceptionCapture
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsErrorCause
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsException
import com.infomaniak.multiplatform_core.organisations.utils.OrganisationsCaller
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrganisationsCallerTest {

    private val crashReport = RecordingCrashReport()
    private val organisationsCaller = OrganisationsCaller(crashReport)

    @Test
    fun unexpectedFailureIsReportedAndWrapped() = runTest {
        val failure = IllegalStateException("boom")

        assertCallerFailsWithCause(OrganisationsErrorCause.Unknown(failure)) { throw failure }
        assertEquals(listOf(ExceptionCapture("Organisations call failed", failure)), crashReport.exceptionCaptures)
    }

    @Test
    fun networkFailureIsNotCaptured() = runTest {
        assertCallerFailsWithCause(OrganisationsErrorCause.Network) { throw IOException("offline") }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    @Test
    fun organisationsFailureIsNotCaptured() = runTest {
        val cause = OrganisationsErrorCause.AccountNotInitialized(ACCOUNT_ID)

        assertCallerFailsWithCause(cause) { throw OrganisationsException(cause) }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    private suspend fun assertCallerFailsWithCause(expected: OrganisationsErrorCause, block: suspend () -> Unit) {
        assertEquals(expected, assertFailsWith<OrganisationsException> { organisationsCaller { block() } }.errorCause)
    }
}
