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
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.kotlin.serialization)
    alias(kmpCalendar.plugins.ksp)
    alias(kmpCalendar.plugins.publish)
}

kotlin {
    android {
        namespace = "com.infomaniak.multiplatform_core.organisations"
        compileSdk = property("kmp.compileSdk").toString().toInt()
        minSdk = property("kmp.minSdk").toString().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        withHostTest {
            isIncludeAndroidResources = false
        }
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":Account"))
            implementation(kmpCalendar.kotlinx.coroutines.core)
            api(project(":Network"))
            api(kmpCalendar.kotlinx.datetime)
            implementation(kmpCalendar.androidx.room.runtime)
            implementation(kmpCalendar.kotlinx.serialization)
            implementation(kmpCalendar.bundles.ktor)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(kmpCalendar.kotlinx.coroutines.test)
            implementation(kmpCalendar.ktor.client.mock)
            implementation(kmpCalendar.androidx.sqlite.bundled)
        }

        getByName("androidHostTest").dependencies {
            implementation(kmpCalendar.android.test)
            implementation(kmpCalendar.robolectric)
            implementation(kmpCalendar.androidx.sqlite.bundled.jvm)
        }

        listOf("iosArm64", "iosSimulatorArm64", "macosArm64").forEach { target ->
            getByName("${target}Test") {
                kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$target/${target}Test/kotlin"))
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }

    explicitApi()
}

dependencies {
    add("kspAndroidHostTest", kmpCalendar.androidx.room.compiler)
    add("kspIosSimulatorArm64Test", kmpCalendar.androidx.room.compiler)
    add("kspIosArm64Test", kmpCalendar.androidx.room.compiler)
    add("kspMacosArm64Test", kmpCalendar.androidx.room.compiler)
}

listOf("IosArm64", "IosSimulatorArm64", "MacosArm64").forEach { target ->
    tasks.named("compileTestKotlin$target") {
        dependsOn("kspTestKotlin$target")
    }
}
