@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import java.lang.module.ModuleFinder.compose


plugins {
    alias(kmpCalendar.plugins.kotlin.multiplatform)
    alias(kmpCalendar.plugins.compose.multiplatform)
    alias(kmpCalendar.plugins.compose.compiler)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "recurrence-js.js"
                output?.apply {
                    library = "RecurrenceWebJs"
                    libraryTarget = "window"
                }
            }
        }
        binaries.executable()
        generateTypeScriptDefinitions()
    }
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "recurrence-wasm.js"
                output?.apply {
                    library = "RecurrenceWebWasm"
                    libraryTarget = "window"
                }
            }
        }
        binaries.executable()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(kmpCalendar.compose.runtime)
            implementation(kmpCalendar.compose.resources)
            implementation(kmpCalendar.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

compose.resources {
    packageOfResClass = "com.infomaniak.calendar.web.resources"
    generateResClass = always
}
