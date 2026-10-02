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

import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ContactsManagerAppleTest {

    @Test
    fun syncTakesRawAccountIds() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        manager.sync(accountIds = setOf(ACCOUNT_ID.value))

        val unknownAccountId = ACCOUNT_ID.value + 1
        assertFailsWithCause(ContactsErrorCause.AccountNotInitialized(AccountId(unknownAccountId))) {
            manager.sync(accountIds = setOf(unknownAccountId))
        }
    }
}
