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
package com.infomaniak.multiplatform_core.network

/** Each module reaches its API at a subdomain of [domain], e.g. `api.infomaniak.com` or `mail.infomaniak.com`. */
public sealed class ApiEnvironment {
    public abstract val domain: String

    public data object Prod : ApiEnvironment() {
        override val domain: String = "infomaniak.com"
    }

    public data object Preprod : ApiEnvironment() {
        override val domain: String = "preprod.dev.infomaniak.ch"
    }

    public data class Custom(override val domain: String) : ApiEnvironment()
}
