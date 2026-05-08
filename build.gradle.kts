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
import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.skie)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ksp)
}

val androidCompileSdk: Int = 36
val androidMinSdk: Int = 27
val javaVersion: JavaVersion = JavaVersion.VERSION_17

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "com.infomaniak.calendar.multiplatform"
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }

    val xcframeworkName = "CoreCalendar"
    val xcf = project.XCFramework(xcframeworkName)
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = xcframeworkName
            binaryOption("bundleId", "com.infomaniak.multiplatform-calendar.${xcframeworkName}")
            xcf.add(this)
            isStatic = true
            linkerOpts.add("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // implementation(core.kotlinx.coroutines.core)
                // implementation(core.kotlinx.serialization.json)
                // implementation(core.kotlinx.serialization.cbor)
                // implementation(core.ktor.client.core)
                // implementation(core.ktor.client.content.negociation)
                // implementation(core.ktor.client.json)
                // implementation(core.ktor.client.encoding)
                // implementation(core.okio)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                // implementation(core.kotlinx.coroutines.test)
                // implementation(core.ktor.client.mock)
            }
        }
        androidMain {
            dependencies {
                // implementation(core.ktor.client.okhttp)
                // implementation(core.splitties.appctx)
            }
        }
        appleMain {
            dependencies {
                // implementation(core.ktor.client.darwin)
            }
        }

        listOf("iosArm64", "iosSimulatorArm64", "macosArm64").forEach { target ->
            getByName("${target}Main") {
                kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$target/${target}Main/kotlin"))
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
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
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspMacosArm64", libs.androidx.room.compiler)
}

listOf("IosArm64", "IosSimulatorArm64", "MacosArm64").forEach { target ->
    tasks.named("compileKotlin$target") {
        dependsOn("kspKotlin$target")
    }
}
