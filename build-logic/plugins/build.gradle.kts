plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        // Register the plugin with the specified ID and implementation class
    }
}

dependencies {
    compileOnly(kmpCalendar.kotlin.gradle.plugin)
}
