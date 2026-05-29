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

import co.touchlab.skie.configuration.DefaultArgumentInterop
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig

plugins {
    alias(kmpCalendar.plugins.android.library)
    alias(kmpCalendar.plugins.gobley.cargo)
    alias(kmpCalendar.plugins.gobley.uniffi)
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.kotlin.serialization)
    alias(kmpCalendar.plugins.ksp)
    alias(kmpCalendar.plugins.metro)
    alias(kmpCalendar.plugins.skie)
    kotlin("plugin.atomicfu") version kmpCalendar.versions.kotlin
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }

    androidTarget()

    val xcFrameworkName = "KmpCalendar"
    val xcf = project.XCFramework(xcFrameworkName)
    iosArm64 { configXCFramework(xcf, xcFrameworkName) }
    iosSimulatorArm64 { configXCFramework(xcf, xcFrameworkName) }
    macosArm64 { configXCFramework(xcf, xcFrameworkName) }

    cargo {
        packageDirectory = layout.projectDirectory.dir("rust/caldav_bridge")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":Core"))
                implementation(kmpCalendar.kotlinx.serialization)
                implementation(kmpCalendar.kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "com.infomaniak.multiplatform_calendar"
    compileSdk = property("kmp.compileSdk").toString().toInt()
    defaultConfig {
        minSdk = property("kmp.minSdk").toString().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

skie {
    features {
        group {
            DefaultArgumentInterop.Enabled(true)
            DefaultArgumentInterop.MaximumDefaultArgumentCount(7)
        }
    }
    build {
        produceDistributableFramework()
    }
}

// Ensure KSP tasks depend on UniFFI binding generation
tasks.configureEach {
    if (name.startsWith("ksp") && name.contains("Kotlin")) {
        dependsOn(tasks.named("buildUniffiBindings"))
    }
}

// Ensure native compilation runs after KSP (Metro code generation)
listOf("IosArm64", "IosSimulatorArm64", "MacosArm64").forEach { target ->
    tasks.named("compileKotlin$target") {
        dependsOn("kspKotlin$target")
    }
}

fun KotlinNativeTarget.configXCFramework(xcf: XCFrameworkConfig, xcFrameworkName: String) {
    binaries.framework {
        baseName = xcFrameworkName
        binaryOption("bundleId", "com.infomaniak.multiplatform-calendar.${xcFrameworkName}")
        xcf.add(this)
        isStatic = true
        linkerOpts.add("-lsqlite3")
        export(project(":Core"))
    }
}
