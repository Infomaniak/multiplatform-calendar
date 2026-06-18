plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("ensureNdkVersion") {
            id = "ensure-ndk-version"
            implementationClass = "com.infomaniak.calendar.buildlogic.ndk.EnsureNdkVersionPlugin"
        }
    }
}

dependencies {
    compileOnly(kmpCalendar.kotlin.gradle.plugin)
    compileOnly("com.android.tools.build:gradle:${kmpCalendar.versions.agp.get()}")
}
