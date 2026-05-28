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
<<<<<<<< HEAD:Core/src/macosMain/kotlin/com/infomaniak/multiplatform_calendar/core/Platform.macos.kt

package com.infomaniak.multiplatform_calendar.core
========
package com.infomaniak.multiplatform_calendar
>>>>>>>> 3da4655 (feat: Add metro and clean dependencies):src/appleMain/kotlin/com/infomaniak/multiplatform_calendar/Platform.apple.kt

import platform.Foundation.NSProcessInfo

class ApplePlatform : Platform {
    override val name: String = NSProcessInfo.processInfo.operatingSystemVersionString
}

actual fun getPlatform(): Platform = ApplePlatform()

