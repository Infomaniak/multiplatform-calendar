import co.touchlab.skie.configuration.DefaultArgumentInterop
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(kmpCalendar.plugins.android.kmp.library)
    alias(kmpCalendar.plugins.androidx.room)
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.kotlin.serialization)
    alias(kmpCalendar.plugins.ksp)
    alias(kmpCalendar.plugins.skie)
}

kotlin {
    android {
        compileSdk { version = release(36) }
        namespace = "com.infomaniak.multiplatform_calendar.core"
        minSdk = 27

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
        withHostTest {}
    }

    val xcframeworkName = "Core"
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
            implementation(kmpCalendar.androidx.room.runtime)
            implementation(kmpCalendar.androidx.sqlite.bundled)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // implementation(core.kotlinx.coroutines.core)
                implementation(kmpCalendar.kotlinx.serialization)
                implementation(kmpCalendar.kotlinx.datetime)
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
