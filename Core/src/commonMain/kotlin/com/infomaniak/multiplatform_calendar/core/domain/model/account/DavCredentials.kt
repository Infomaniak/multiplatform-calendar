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
package com.infomaniak.multiplatform_calendar.core.domain.model.account

/**
 * CalDAV account credentials, as seen by public consumers of the library.
 *
 * This is the Core-owned counterpart of the internal `:kmpdav` `DavAccount`
 * (mapped at the repository boundary): keeping a dedicated domain type here lets
 * the bridge module stay an implementation detail that is never exported into
 * the public Apple framework.
 */
public data class DavCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
)

