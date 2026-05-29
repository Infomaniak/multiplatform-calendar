plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("infomaniak.rustToolchain") {
            id = "infomaniak.rustToolchain"
            implementationClass = "com.infomaniak.multiplatform_calendar.plugins.rust.RustToolchainPlugin"
        }
    }
}

dependencies {
    compileOnly(kmpCalendar.kotlin.gradle.plugin)
}
