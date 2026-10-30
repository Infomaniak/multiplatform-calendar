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
package com.infomaniak.multiplatform_core.contacts

import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import com.infomaniak.multiplatform_core.contacts.utils.ContactsCaller
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport
import com.infomaniak.multiplatform_core.crashreport.RecordingCrashReport.ExceptionCapture
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactsCallerTest {

    private val crashReport = RecordingCrashReport()
    private val contactsCaller = ContactsCaller(crashReport)

    @Test
    fun unexpectedFailureIsReportedAndWrapped() = runTest {
        val failure = IllegalStateException("boom")

        assertFailsWithCause(ContactsErrorCause.Unknown(failure)) { contactsCaller { throw failure } }
        assertEquals(listOf(ExceptionCapture("Contacts call failed", failure)), crashReport.exceptionCaptures)
    }

    @Test
    fun networkFailureIsNotCaptured() = runTest {
        assertFailsWithCause(ContactsErrorCause.Network) { contactsCaller { throw IOException("offline") } }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }

    @Test
    fun contactsFailureIsNotCaptured() = runTest {
        val cause = ContactsErrorCause.AccountNotInitialized(ACCOUNT_ID)

        assertFailsWithCause(cause) { contactsCaller { throw ContactsException(cause) } }
        assertTrue(crashReport.exceptionCaptures.isEmpty())
    }
}
