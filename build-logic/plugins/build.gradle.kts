plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("infomaniak.publishPlugin") {
            id = "infomaniak.publishPlugin"
            implementationClass = "com.infomaniak.calendar.buildlogic.publish.PublishPlugin"
        }
    }
}

dependencies {
    compileOnly(kmpCalendar.kotlin.gradle.plugin)
    compileOnly(kmpCalendar.android.gradle.plugin)
}
