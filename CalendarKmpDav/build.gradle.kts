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
    alias(kmpCalendar.plugins.android.kmp.library)
    alias(kmpCalendar.plugins.ensureNdkVersion)
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.kotlin.serialization)
    alias(kmpCalendar.plugins.ksp)
    alias(kmpCalendar.plugins.metro)
    alias(kmpCalendar.plugins.ubique.uniffi)
    id("infomaniak.publishPlugin")
}

uniffi {
    // The plugin defaults to HEAD of the bindgen repository; pin it to the plugin's own tag so
    // the generated bindings are reproducible and stay in sync with the runtime.
    bindgenFromGitTag(
        repository = "https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings.git",
        tag = "v${kmpCalendar.versions.ubiqueUniffi.get()}",
    )

    generateFromLibrary()
}

ensureNdkVersion {
    // The 16 KB page size Android 15+ requires needs NDK r28 (30.x) or newer. Declared as a
    // minimum: a newer installed NDK is reused instead of forcing this exact one to be downloaded.
    minimumVersion = "30.0.14904198"
}

cargo {
    packageDirectory = layout.projectDirectory.dir("rust/caldav_bridge")
    // `com.android.kotlin.multiplatform.library` has no `ndkVersion` of its own, so the NDK the
    // Android targets are cross-compiled with is set here instead.
    //
    // It must come from `resolvedVersion` rather than be repeated as a literal: the UniFFI plugin
    // silently falls back to the newest installed NDK when the requested one is missing, so a
    // hardcoded version would produce a green build against an unintended toolchain. Reading the
    // resolved value is also what triggers the install when nothing satisfies the minimum.
    ndkVersion = ensureNdkVersion.resolvedVersion
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }

    android {
        namespace = "com.infomaniak.multiplatform_calendar"
        compileSdk = property("kmp.compileSdk").toString().toInt()
        minSdk = property("kmp.minSdk").toString().toInt()

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonMain {
            dependencies {
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

// Ensure KSP tasks depend on UniFFI binding generation
tasks.configureEach {
    if (name.startsWith("ksp") && name.contains("Kotlin")) {
        dependsOn(tasks.named("buildBindings"))
    }
}
// Ensure native compilation runs after KSP (Metro code generation)
listOf("IosArm64", "IosSimulatorArm64", "MacosArm64").forEach { target ->
    tasks.matching { it.name == "compileKotlin$target" }.configureEach {
        dependsOn("kspKotlin$target")
    }
}
