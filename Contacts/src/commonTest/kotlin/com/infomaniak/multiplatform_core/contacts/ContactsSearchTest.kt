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
    fun sortsByContactedTimesThenName() = runTest {
        val manager = testManager(
            apiContacts = listOf(
                ApiContact(name = "Adam", emails = listOf("adam@x.com")),
                ApiContact(name = "Anna", emails = listOf("anna@x.com"), contactedTimes = mapOf("anna@x.com" to 3)),
                ApiContact(name = "Amanda", emails = listOf("amanda@x.com"), other = true),
            ),
        )

        assertEquals(listOf("Anna", "Adam", "Amanda"), manager.search("a", setOf(ACCOUNT_ID)).map { it.name })
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
}
