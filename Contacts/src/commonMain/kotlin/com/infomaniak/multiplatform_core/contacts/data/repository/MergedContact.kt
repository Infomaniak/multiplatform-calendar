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
package com.infomaniak.multiplatform_core.contacts.data.repository

import com.infomaniak.multiplatform_core.contacts.domain.model.Contact

/** Merged contact used for sorting, before being mapped to [Contact]. */
internal data class MergedContact(
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val comesFromApi: Boolean,
    val contactedTimes: Int?,
    val isInAddressBook: Boolean,
) {
    /** Identity of a contact: the (email, name) pair, emails being case insensitive. */
    val key: Pair<String, String> get() = email.lowercase() to name

    fun toContact(): Contact = Contact(email = email, name = name, avatarUrl = avatarUrl, comesFromApi = comesFromApi)
}
