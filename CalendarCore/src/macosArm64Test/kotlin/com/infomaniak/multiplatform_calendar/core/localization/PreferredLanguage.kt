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
package com.infomaniak.multiplatform_calendar.core.localization

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import kotlin.test.assertEquals

internal suspend fun withPreferredLanguage(language: String, block: suspend () -> Unit) {
    val defaults = NSUserDefaults.standardUserDefaults
    val key = "AppleLanguages"
    val previous = defaults.objectForKey(key)

    try {
        defaults.setObject(arrayListOf(language), forKey = key)
        defaults.synchronize()
        val currentLanguage = NSLocale.preferredLanguages.firstOrNull() as? String
        assertEquals(language, currentLanguage, "test locale did not apply")
        block()
    } finally {
        if (previous == null) defaults.removeObjectForKey(key)
        else defaults.setObject(previous, forKey = key)
        defaults.synchronize()
    }
}
