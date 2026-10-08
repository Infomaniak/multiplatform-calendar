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

import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactsObserveTest : RobolectricTestsBase() {

    @Test
    fun findsTheContactOfAnEmailWhateverItsCase() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        val contacts = manager.observeContacts(setOf("John@X.com")).first()

        assertEquals(listOf("John@X.com"), contacts.keys.toList())
        assertEquals("John", contacts.getValue("John@X.com").name)
    }

    @Test
    fun leavesOutAnEmailWithoutContact() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        assertEquals(emptyMap(), manager.observeContacts(setOf("jane@x.com")).first())
    }

    @Test
    fun picksTheBestRankedContactAmongThoseSharingAnEmail() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Johnny", emails = listOf("john@x.com")),
                ApiContact(name = "John", emails = listOf("john@x.com"), contactedTimes = mapOf("john@x.com" to 5)),
            ),
        )

        assertEquals("John", manager.observeContacts(setOf("john@x.com")).first()["john@x.com"]?.name)
    }

    @Test
    fun findsADeviceContact() = runTest {
        val manager = testManager(deviceContacts = listOf(DeviceContact(email = "dana@x.com", name = "Dana")))

        assertEquals(
            Contact(email = "dana@x.com", name = "Dana", avatar = null, comesFromApi = false),
            manager.observeContacts(setOf("dana@x.com")).first().getValue("dana@x.com"),
        )
    }

    @Test
    fun emitsAgainOnceASyncBringsTheContact() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))), initAccount = false)
        val emissions = Channel<Map<String, Contact>>(Channel.UNLIMITED)
        val collection = launch { manager.observeContacts(setOf("john@x.com")).collect(emissions::send) }

        assertEquals(emptyMap(), emissions.receive())
        manager.initTestAccount()
        manager.sync(setOf(ACCOUNT_ID))
        assertEquals("John", emissions.receive().getValue("john@x.com").name)

        collection.cancel()
    }

    @Test
    fun emitsAgainOnceTheAccessToTheDeviceContactsIsGranted() = runTest {
        val deviceContactsProvider = FakeDeviceContactsProvider(initialContacts = null)
        val manager = ContactsManager(
            database = testDatabase(),
            deviceContactsProvider = deviceContactsProvider,
            httpClient = testHttpClient(apiContacts = emptyList(), etag = null),
        )
        val emissions = Channel<Map<String, Contact>>(Channel.UNLIMITED)
        val collection = launch { manager.observeContacts(setOf("dana@x.com")).collect(emissions::send) }

        assertEquals(emptyMap(), emissions.receive())
        deviceContactsProvider.contacts = listOf(DeviceContact(email = "dana@x.com", name = "Dana"))
        manager.onDeviceContactsAccessGranted()
        assertEquals("Dana", emissions.receive().getValue("dana@x.com").name)

        collection.cancel()
    }

    @Test
    fun emitsAgainWhenTheDeviceContactsChange() = runTest {
        val deviceContactsProvider = FakeDeviceContactsProvider(emptyList())
        val manager = ContactsManager(
            database = testDatabase(),
            deviceContactsProvider = deviceContactsProvider,
            httpClient = testHttpClient(apiContacts = emptyList(), etag = null),
        )
        val emissions = Channel<Map<String, Contact>>(Channel.UNLIMITED)
        val collection = launch { manager.observeContacts(setOf("dana@x.com")).collect(emissions::send) }

        assertEquals(emptyMap(), emissions.receive())
        deviceContactsProvider.contacts = listOf(DeviceContact(email = "dana@x.com", name = "Dana"))
        deviceContactsProvider.systemChanges.emit(Unit)
        assertEquals("Dana", emissions.receive().getValue("dana@x.com").name)

        collection.cancel()
    }
}
