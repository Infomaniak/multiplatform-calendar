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
package com.infomaniak.multiplatform_calendar.bookable_resources.domain.model.exceptions

import com.infomaniak.multiplatform_core.account.domain.model.AccountId

public sealed class MeetingRoomsErrorCause {

    internal abstract val message: String

    public data class AccountNotInitialized(val accountId: AccountId) : MeetingRoomsErrorCause() {
        override val message: String get() = "Account $accountId is not initialized"
    }

    public data object Network : MeetingRoomsErrorCause() {
        override val message: String get() = "Network error"
    }

    public data class Api(val statusCode: Int) : MeetingRoomsErrorCause() {
        override val message: String get() = "Unexpected HTTP status $statusCode"
    }

    public data class Unknown(val throwable: Throwable) : MeetingRoomsErrorCause() {
        override val message: String get() = "Unexpected error"
    }
}
