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
package com.infomaniak.multiplatform_core.contacts.domain.model

import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

public sealed interface ContactAvatar {

    /** An image of the Infomaniak API, to be fetched with the token of [accountId]. */
    @OptIn(ExperimentalObjCRefinement::class)
    public data class Remote(
        val url: String,
        @HiddenFromObjC
        val accountId: AccountId,
    ) : ContactAvatar

    /**
     * The photo of a device contact: its thumbnail content URI on Android, its `CNContact` identifier on Apple, to load
     * with `ContactsManager.avatarData`.
     */
    public data class Device(val id: String) : ContactAvatar
}
