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
plugins {
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.android.kmp.library)
    // alias(kmpCalendar.plugins.kotlin.atomicfu)
    // alias(kmpCalendar.plugins.gobley.cargo)
    // alias(kmpCalendar.plugins.gobley.uniffi)
}

kotlin {
    android {
        compileSdk { version = release(36) }
        namespace = "com.infomaniak.multiplatform_calendar.kmpdav"
        minSdk = 27
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            // Public API only depends on generated UniFFI bindings.
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// cargo {
//     packageDirectory = layout.projectDirectory
// }

// uniffi {
//     packageName = "com.infomaniak.kmpdav.internal"
// }
