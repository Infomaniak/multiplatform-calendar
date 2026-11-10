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
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactsFindContactTest : RobolectricTestsBase() {

    @Test
    fun findsTheContactOfAnEmailWhateverItsCase() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        assertEquals("John", manager.findContact("John@X.com")?.name)
    }

    @Test
    fun findsNothingForAnEmailWithoutContact() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        assertNull(manager.findContact("jane@x.com", name = "John"))
    }

    @Test
    fun findsAContactWhoseEmailDiffersOnlyByAccents() = runTest {
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "Jöhn", emails = listOf("jöhn@x.com"))),
            deviceContacts = listOf(DeviceContact(email = "dänä@x.com", name = "Dana")),
        )

        assertEquals("jöhn@x.com", manager.findContact("john@x.com")?.email)
        assertEquals("dänä@x.com", manager.findContact("dana@x.com")?.email)
    }

    @Test
    fun prefersTheSameEmailOverTheName() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "John", emails = listOf("john@x.com")),
                ApiContact(name = "Jöhn", emails = listOf("jöhn@x.com"), contactedTimes = mapOf("jöhn@x.com" to 5)),
            ),
        )

        assertEquals("john@x.com", manager.findContact("john@x.com", name = "Jöhn")?.email)
        assertEquals("jöhn@x.com", manager.findContact("JÖHN@x.com", name = "John")?.email)
    }

    @Test
    fun findsNothingForABlankEmail() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        assertNull(manager.findContact(" ", name = "John"))
    }

    @Test
    fun prefersTheContactNamedAsRequestedOverABetterRankedOne() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Johnny", emails = listOf("john@x.com"), contactedTimes = mapOf("john@x.com" to 5)),
                ApiContact(name = "John", emails = listOf("john@x.com")),
            ),
        )

        assertEquals("John", manager.findContact("john@x.com", name = "John")?.name)
    }

    @Test
    fun matchesTheNameWhateverItsCaseAndAccents() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Johnny", emails = listOf("elodie@x.com"), contactedTimes = mapOf("elodie@x.com" to 5)),
                ApiContact(name = "Élodie", emails = listOf("elodie@x.com")),
            ),
        )

        assertEquals("Élodie", manager.findContact("elodie@x.com", name = "ELODIE")?.name)
    }

    @Test
    fun prefersTheExactNameOverOneDifferingByCaseOrAccents() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "elodie", emails = listOf("elodie@x.com"), contactedTimes = mapOf("elodie@x.com" to 5)),
                ApiContact(name = "Élodie", emails = listOf("elodie@x.com")),
            ),
        )

        assertEquals("Élodie", manager.findContact("elodie@x.com", name = "Élodie")?.name)
        assertEquals("elodie", manager.findContact("elodie@x.com", name = "elodie")?.name)
    }

    @Test
    fun fallsBackOnTheBestRankedContactWhenNoneHasTheName() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Johnny", emails = listOf("john@x.com")),
                ApiContact(name = "John", emails = listOf("john@x.com"), contactedTimes = mapOf("john@x.com" to 5)),
            ),
        )

        assertEquals("John", manager.findContact("john@x.com", name = "Jack")?.name)
        assertEquals("John", manager.findContact("john@x.com")?.name)
    }

    @Test
    fun findsADeviceContact() = runTest {
        val manager = testManager(deviceContacts = listOf(DeviceContact(email = "dana@x.com", name = "Dana")))

        assertEquals(
            Contact(email = "dana@x.com", name = "Dana", avatar = null, comesFromApi = false),
            manager.findContact("Dana@x.com", name = "Dana"),
        )
    }

    @Test
    fun searchesOnlyTheRequestedAccounts() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        assertEquals("John", manager.findContact("john@x.com", accountIds = setOf(ACCOUNT_ID))?.name)
        assertNull(manager.findContact("john@x.com", accountIds = setOf(AccountId(ACCOUNT_ID.value + 1))))
    }
}
