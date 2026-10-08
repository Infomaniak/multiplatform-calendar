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
package com.infomaniak.multiplatform_calendar.core.repository.event

import com.infomaniak.multiplatform_calendar.core.data.repository.ContactsLookup
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeContactsLookup : ContactsLookup {

    val contacts = MutableStateFlow<List<Contact>>(emptyList())
    val preferredAccountIds = mutableListOf<AccountId>()

    override fun observeContacts(emails: Set<String>, preferredAccountId: AccountId): Flow<Map<String, Contact>> {
        preferredAccountIds += preferredAccountId
        return contacts.map { contacts ->
            emails.mapNotNull { email -> contacts.firstOrNull { it.email == email }?.let { email to it } }.toMap()
        }
    }
}
