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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(kmpCalendar.plugins.android.kmp.library)
    alias(kmpCalendar.plugins.compose.compiler)
    alias(kmpCalendar.plugins.compose.multiplatform)
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.publish)
}

kotlin {
    android {
        namespace = "com.infomaniak.multiplatform_calendar.resources"
        compileSdk = property("kmp.compileSdk").toString().toInt()
        minSdk = property("kmp.minSdk").toString().toInt()
        androidResources.enable = true

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            // Exposed so CalendarCore can call getString/getPluralString while keeping
            // every XML resource and generated Res accessor in this dedicated module.
            api(kmpCalendar.compose.resources)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.infomaniak.multiplatform_calendar.resources"
}
