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
package com.infomaniak.multiplatform_core.contacts.utils

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSCaseInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.NSWidthInsensitiveSearch
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.stringByFoldingWithOptions

@OptIn(BetaInteropApi::class)
internal actual fun String.caseFoldedDecomposition(): String {
    val folded = NSString.create(string = this).stringByFoldingWithOptions(
        options = NSCaseInsensitiveSearch or NSWidthInsensitiveSearch,
        locale = null,
    )
    return NSString.create(string = folded).decomposedStringWithCompatibilityMapping
}

@OptIn(BetaInteropApi::class)
internal actual fun String.canonicalComposition(): String {
    return NSString.create(string = this).precomposedStringWithCanonicalMapping
}
