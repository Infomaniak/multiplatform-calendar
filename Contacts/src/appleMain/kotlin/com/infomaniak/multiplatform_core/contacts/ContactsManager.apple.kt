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
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import com.infomaniak.multiplatform_core.contacts.domain.model.ContactAvatar
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import platform.Foundation.NSData
import kotlin.coroutines.cancellation.CancellationException

// Swift cannot build a set of AccountId (a value class is boxed inside a collection), so it passes raw ids.

/** [ContactsManager.search] for Swift, with raw account ids. */
@Throws(ContactsException::class, CancellationException::class)
public suspend fun ContactsManager.search(
    query: String,
    accountIds: Set<Long> = emptySet(),
    limit: Int = ContactsManager.DEFAULT_SEARCH_LIMIT,
): List<Contact> = search(query, accountIds.toAccountIds(), limit)

/** [ContactsManager.sync] for Swift, with raw account ids. */
@Throws(ContactsException::class, CancellationException::class)
public suspend fun ContactsManager.sync(accountIds: Set<Long> = emptySet()): Unit = sync(accountIds.toAccountIds())

/** The thumbnail of [avatar], or null when it is gone or access to the contacts is not granted. */
public suspend fun ContactsManager.avatarData(avatar: ContactAvatar.Device): NSData? = deviceContactThumbnail(avatar.id)

private fun Set<Long>.toAccountIds(): Set<AccountId> = mapTo(mutableSetOf(), ::AccountId)
