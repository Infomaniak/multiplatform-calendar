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
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import kotlin.coroutines.cancellation.CancellationException

// Swift cannot build a set of AccountId (a value class is boxed inside a collection), so it passes raw ids.

/** [ContactsManager.sync] for Swift, with raw account ids. */
@Throws(ContactsException::class, CancellationException::class)
public suspend fun ContactsManager.sync(accountIds: Set<Long> = emptySet()): Unit = sync(accountIds.toAccountIds())

private fun Set<Long>.toAccountIds(): Set<AccountId> = mapTo(mutableSetOf(), ::AccountId)
