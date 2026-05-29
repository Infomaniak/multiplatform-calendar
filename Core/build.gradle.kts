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
    alias(kmpCalendar.plugins.skie)
}

kotlin {
    androidTarget()

    val xcFrameworkName = "MultiplatformCore"
    val xcf = project.XCFramework(xcFrameworkName)
    iosArm64 { configXCFramework(xcf, xcFrameworkName) }
    iosSimulatorArm64 { configXCFramework(xcf, xcFrameworkName) }
    macosArm64 { configXCFramework(xcf, xcFrameworkName) }

    sourceSets {
        commonMain.dependencies {
            implementation(kmpCalendar.androidx.room.runtime)
            implementation(kmpCalendar.androidx.sqlite.bundled)
            implementation(kmpCalendar.kotlinx.serialization)
            implementation(kmpCalendar.kotlinx.datetime)
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

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }
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

// Provide a consumable variant matching the "dev.gobley.kind = UNIFFI" attribute
// so that the parent module's uniFfiConfiguration can resolve this project dependency.
val kindAttribute = Attribute.of("dev.gobley.kind", String::class.java)
configurations.consumable("uniFfiConfigurationConsumable") {
    attributes.attribute(kindAttribute, "UNIFFI")
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



fun KotlinNativeTarget.configXCFramework(xcf: XCFrameworkConfig, xcFrameworkName: String) {
    binaries.framework {
        baseName = xcFrameworkName
        binaryOption("bundleId", "com.infomaniak.multiplatform-calendar.${xcFrameworkName}")
        xcf.add(this)
        isStatic = true
        linkerOpts.add("-lsqlite3")
    }
}
