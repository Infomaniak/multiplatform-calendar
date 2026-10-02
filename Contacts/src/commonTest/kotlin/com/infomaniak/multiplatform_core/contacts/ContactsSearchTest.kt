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

import com.infomaniak.multiplatform_core.contacts.data.remote.MAIL_API_HOST
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactsSearchTest : RobolectricTestsBase() {

    @Test
    fun emptyQueryReturnsNoResult() = runTest {
        val manager = testManager(apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))))

        assertEquals(emptyList(), manager.search("", setOf(ACCOUNT_ID)))
        assertEquals(emptyList(), manager.search("   ", setOf(ACCOUNT_ID)))
    }

    @Test
    fun mergesDeviceAndApiContactsSharingSameEmailAndName() = runTest {
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "John Doe", emails = listOf("john@x.com"), avatar = "/avatar/john.png")),
            deviceContacts = listOf(DeviceContact(email = "john@x.com", name = "John Doe")),
        )

        val results = manager.search("john", setOf(ACCOUNT_ID))

        assertEquals(
            listOf(
                Contact(
                    email = "john@x.com",
                    name = "John Doe",
                    avatarUrl = "https://$MAIL_API_HOST/avatar/john.png",
                    comesFromApi = true,
                ),
            ),
            results,
        )
    }

    @Test
    fun mergesContactsWhoseEmailsOnlyDifferInCase() = runTest {
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "John", emails = listOf("john@x.com"))),
            deviceContacts = listOf(DeviceContact(email = "John@X.com", name = "John")),
        )

        assertEquals(1, manager.search("john", setOf(ACCOUNT_ID)).size)
    }

    @Test
    fun sortsByContactedTimesThenApiFirstThenName() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Adam", emails = listOf("adam@x.com")),
                ApiContact(name = "Anna", emails = listOf("anna@x.com"), contactedTimes = mapOf("anna@x.com" to 3)),
                ApiContact(name = "Amanda", emails = listOf("amanda@x.com"), other = true),
            ),
            deviceContacts = listOf(DeviceContact(email = "axel@x.com", name = "Axel")),
        )

        assertEquals(listOf("Anna", "Adam", "Axel", "Amanda"), manager.search("a", setOf(ACCOUNT_ID)).map { it.name })
    }

    @Test
    fun sortsContactsSharingSameWeightByName() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Zack", emails = listOf("zack@x.com"), contactedTimes = mapOf("zack@x.com" to 1)),
                ApiContact(name = "Albert", emails = listOf("albert@x.com"), contactedTimes = mapOf("albert@x.com" to 1)),
            ),
        )

        assertEquals(listOf("Albert", "Zack"), manager.search("a", setOf(ACCOUNT_ID)).map { it.name })
    }

    @Test
    fun ranksStartOfTextThenStartOfWordThenInsideMatches() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Eduard", emails = listOf("e@x.com")),
                ApiContact(name = "Martin Dupont", emails = listOf("m@x.com")),
                ApiContact(name = "Dumas", emails = listOf("d@x.com")),
            ),
        )

        assertEquals(listOf("Dumas", "Martin Dupont", "Eduard"), manager.search("du", setOf(ACCOUNT_ID)).map { it.name })
    }

    @Test
    fun ranksContactedTimesBeforeMatchQuality() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Dumas", emails = listOf("d@x.com")),
                ApiContact(name = "Eduard", emails = listOf("e@x.com"), contactedTimes = mapOf("e@x.com" to 2)),
            ),
        )

        assertEquals(listOf("Eduard", "Dumas"), manager.search("du", setOf(ACCOUNT_ID)).map { it.name })
    }

    @Test
    fun searchIsAccentAndCaseInsensitive() = runTest {
        val manager = testManager(
            apiContacts = listOf(ApiContact(name = "Éléonore Dupont", emails = listOf("eleonore@x.com"))),
        )

        assertEquals(1, manager.search("eleonore", setOf(ACCOUNT_ID)).size)
        assertEquals(1, manager.search("ELEONORE", setOf(ACCOUNT_ID)).size)
        assertEquals(1, manager.search("dupont", setOf(ACCOUNT_ID)).size)
        assertEquals(0, manager.search("xyz", setOf(ACCOUNT_ID)).size)
    }

    @Test
    fun likeWildcardsInQueryAreMatchedLiterally() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "John", emails = listOf("john@x.com")),
                ApiContact(name = "Promo 50%", emails = listOf("promo@x.com")),
            ),
        )

        assertEquals(listOf("Promo 50%"), manager.search("%", setOf(ACCOUNT_ID)).map { it.name })
        assertEquals(0, manager.search("_", setOf(ACCOUNT_ID)).size)
    }

    @Test
    fun searchRespectsLimit() = runTest {
        val manager = testManager(
            apiContacts = (1..ContactsManager.DEFAULT_SEARCH_LIMIT * 2).map {
                ApiContact(name = "Contact $it", emails = listOf("contact$it@x.com"))
            },
        )

        assertEquals(3, manager.search("contact", setOf(ACCOUNT_ID), limit = 3).size)
        assertEquals(ContactsManager.DEFAULT_SEARCH_LIMIT, manager.search("contact", setOf(ACCOUNT_ID)).size)
    }

    @Test
    fun deviceContactsAreCachedUntilTheSystemReportsAChange() = runTest {
        val provider = FakeDeviceContactsProvider(listOf(DeviceContact(email = "john@x.com", name = "John")))
        val manager = deviceContactsManager(provider)
        manager.search("john", setOf(ACCOUNT_ID))
        manager.search("john", setOf(ACCOUNT_ID))

        provider.contacts = listOf(DeviceContact(email = "jane@x.com", name = "Jane"))
        assertEquals(0, manager.search("jane", setOf(ACCOUNT_ID)).size)
        assertEquals(2, provider.readCount)

        provider.systemChanges.emit(Unit)

        assertEquals(1, manager.search("jane", setOf(ACCOUNT_ID)).size)
        assertEquals(3, provider.readCount)
    }

    @Test
    fun aChangeMadeWhileTheObserverStartsIsNotMissed() = runTest {
        val provider = FakeDeviceContactsProvider(listOf(DeviceContact(email = "john@x.com", name = "John")))
        val manager = deviceContactsManager(provider)
        manager.search("john", setOf(ACCOUNT_ID))

        provider.contacts = listOf(DeviceContact(email = "jane@x.com", name = "Jane"))

        assertEquals(1, manager.search("jane", setOf(ACCOUNT_ID)).size)
    }

    @Test
    fun deniedDeviceContactsReadIsNotCached() = runTest {
        val provider = FakeDeviceContactsProvider(initialContacts = null)
        val manager = deviceContactsManager(provider)

        assertEquals(0, manager.search("john", setOf(ACCOUNT_ID)).size)
        provider.contacts = listOf(DeviceContact(email = "john@x.com", name = "John"))

        assertEquals(1, manager.search("john", setOf(ACCOUNT_ID)).size)
    }

    /** Unconfined, so that the change observer runs before the next search. */
    private suspend fun deviceContactsManager(provider: FakeDeviceContactsProvider) = ContactsManager(
        database = testDatabase(),
        deviceContactsProvider = provider,
        httpClient = testHttpClient(apiContacts = emptyList(), etag = null),
        deviceContactsScope = CoroutineScope(Dispatchers.Unconfined),
    ).apply { initTestAccount() }
}
