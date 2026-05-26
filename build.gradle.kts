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
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    kotlin("plugin.atomicfu") version kmpCalendar.versions.kotlin
    alias(kmpCalendar.plugins.kotlin.serialization)
    alias(kmpCalendar.plugins.skie)
    alias(kmpCalendar.plugins.androidx.room)
    alias(kmpCalendar.plugins.ksp)
    alias(kmpCalendar.plugins.gobley.cargo)
    alias(kmpCalendar.plugins.gobley.uniffi)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }

    android {
        namespace = "com.infomaniak.calendar.multiplatform"
        compileSdk = 36
        defaultConfig {
            minSdk = 27
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
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
                implementation(kmpCalendar.androidx.room.runtime)
                implementation(kmpCalendar.androidx.sqlite.bundled)
                implementation(kmpCalendar.kotlinx.serialization)
                implementation(kmpCalendar.kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        androidMain {
            dependencies {
            }
        }
        appleMain {
            dependencies {
            }
        }

        listOf("iosArm64", "iosSimulatorArm64", "macosArm64").forEach { target ->
            getByName("${target}Main") {
                kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$target/${target}Main/kotlin"))
            }
        }
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

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", kmpCalendar.androidx.room.compiler)
    add("kspIosSimulatorArm64", kmpCalendar.androidx.room.compiler)
    add("kspIosArm64", kmpCalendar.androidx.room.compiler)
    add("kspMacosArm64", kmpCalendar.androidx.room.compiler)
}

listOf("IosArm64", "IosSimulatorArm64", "MacosArm64").forEach { target ->
    tasks.named("compileKotlin$target") {
        dependsOn("kspKotlin$target")
    }
}

// Ensure KSP tasks depend on UniFFI binding generation
tasks.configureEach {
    if (name.startsWith("ksp") && name.contains("Kotlin")) {
        dependsOn(tasks.named("buildUniffiBindings"))
    }
}

fun KotlinNativeTarget.configXCFramework(xcf: XCFrameworkConfig, xcFrameworkName: String) {
    binaries.framework {
        baseName = xcFrameworkName
        binaryOption("bundleId", "com.infomaniak.multiplatform-calendar.${xcFrameworkName}")
        xcf.add(this)
        isStatic = true
        linkerOpts.add("-lsqlite3")
    }
}
