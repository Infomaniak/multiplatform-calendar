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

import kotlinx.coroutines.flow.Flow

/** Provides the device contacts that have at least one email address. */
public interface DeviceContactsProvider {

    /** Returns null when access to the contacts is not granted. */
    public suspend fun read(): List<DeviceContact>?

    /**
     * Emits once registered, so that a change made since [read] is not missed, then on every change.
     * Only collected once [read] succeeded.
     */
    public val changes: Flow<Unit>
}
