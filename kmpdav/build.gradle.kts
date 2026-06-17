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
    alias(kmpCalendar.plugins.android.library)
    alias(kmpCalendar.plugins.gobley.cargo)
    alias(kmpCalendar.plugins.gobley.uniffi)
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.kotlin.serialization)
    alias(kmpCalendar.plugins.ksp)
    alias(kmpCalendar.plugins.metro)
    kotlin("plugin.atomicfu") version kmpCalendar.versions.kotlin
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }

    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    cargo {
        packageDirectory = layout.projectDirectory.dir("rust/caldav_bridge")
        // Always compile the Rust .so with Cargo's `release` profile, even for Android Debug
        // builds: a Rust debug .so weighs ~80 MB/ABI vs ~10 MB in release, which would otherwise
        // bloat the debug APK by ~150 MB. The release profile here is the soft one in Cargo.toml
        // (opt-level "s", lto, strip), not the extreme size-optimized one.
        debug.profile = gobley.gradle.cargo.profiles.CargoProfile.Release
        // Kotlin/Native embeds the Rust static lib at cinterop time, which is
        // variant-agnostic (a single klib). Gobley defaults the native Rust build
        // to `Debug`, so even `assembleKmpCalendarReleaseXCFramework` would link
        // the huge (~140 MB) debug `.a` into the *release* XCFramework.
        // Opt in (used by `buildRelease`) to compile the native Rust in release
        // (~14 MB) without forcing slow/optimized Rust on day-to-day Apple builds.
        if (providers.gradleProperty("rustNativeRelease").orNull.toBoolean()) {
            nativeVariant = gobley.gradle.Variant.Release
        }
    }

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
// Ensure KSP tasks depend on UniFFI binding generation
tasks.configureEach {
    if (name.startsWith("ksp") && name.contains("Kotlin")) {
        dependsOn(tasks.named("buildUniffiBindings"))
    }
}
// Ensure native compilation runs after KSP (Metro code generation)
listOf("IosArm64", "IosSimulatorArm64", "MacosArm64").forEach { target ->
    tasks.matching { it.name == "compileKotlin$target" }.configureEach {
        dependsOn("kspKotlin$target")
    }
}
