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
    alias(kmpCalendar.plugins.androidx.room)
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.kotlin.serialization)
    alias(kmpCalendar.plugins.ksp)
    alias(kmpCalendar.plugins.metro)
    alias(kmpCalendar.plugins.skie)
    alias(kmpCalendar.plugins.publish)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    val xcFrameworkName = "MultiplatformCalendar"
    val xcf = project.XCFramework(xcFrameworkName)
    iosArm64 { configXCFramework(xcf, xcFrameworkName) }
    iosSimulatorArm64 { configXCFramework(xcf, xcFrameworkName) }
    macosArm64 { configXCFramework(xcf, xcFrameworkName) }

    sourceSets {
        commonMain.dependencies {
            api(project(":CalendarKmpDav"))
            implementation(kmpCalendar.androidx.room.runtime)
            implementation(kmpCalendar.androidx.sqlite.bundled)
            implementation(kmpCalendar.kotlinx.serialization)
            implementation(kmpCalendar.kotlinx.datetime)
            implementation(kmpCalendar.bundles.ktor)

            implementation(kmpCalendar.material.kolor)
        }

        androidMain.dependencies {
            implementation(kmpCalendar.ktor.client.okhttp)
            implementation(kmpCalendar.kotlinx.coroutines.android)
        }

        appleMain.dependencies {
            implementation(kmpCalendar.ktor.client.darwin)
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
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

    explicitApi()
}

android {
    namespace = "com.infomaniak.multiplatform_calendar.core"
    compileSdk = property("kmp.compileSdk").toString().toInt()
    defaultConfig {
        minSdk = property("kmp.minSdk").toString().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Provide a consumable variant matching the "dev.gobley.kind = UNIFFI" attribute
// so that the parent module's uniFfiConfiguration can resolve this project dependency.
val kindAttribute = Attribute.of("dev.gobley.kind", String::class.java)
configurations.consumable("uniFfiConfigurationConsumable") {
    attributes.attribute(kindAttribute, "UNIFFI")
}

room {
    schemaDirectory("$projectDir/schemas")
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

fun KotlinNativeTarget.configXCFramework(xcf: XCFrameworkConfig, xcFrameworkName: String) {
    binaries.framework {
        baseName = xcFrameworkName
        binaryOption("bundleId", "com.infomaniak.multiplatform-calendar.${xcFrameworkName}")
        xcf.add(this)
        isStatic = true
        linkerOpts.add("-lsqlite3")
    }
}
