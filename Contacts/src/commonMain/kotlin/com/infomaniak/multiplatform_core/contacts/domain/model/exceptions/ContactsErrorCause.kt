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
package com.infomaniak.multiplatform_core.contacts.domain.model.exceptions

import com.infomaniak.multiplatform_core.account.domain.model.AccountId

public sealed class ContactsErrorCause {

    internal abstract val message: String

    /** [accountId] was not registered with `ContactsManager.initAccount`. */
    public data class AccountNotInitialized(val accountId: AccountId) : ContactsErrorCause() {
        override val message: String get() = "Account $accountId is not initialized"
    }

    /** The server could not be reached. */
    public data object Network : ContactsErrorCause() {
        override val message: String get() = "Network error"
    }

    /** The API answered with an unexpected HTTP [statusCode]. */
    public data class Api(val statusCode: Int) : ContactsErrorCause() {
        override val message: String get() = "Unexpected HTTP status $statusCode"
    }

    public data class Unknown(val throwable: Throwable) : ContactsErrorCause() {
        override val message: String get() = "Unexpected error"
    }
}
