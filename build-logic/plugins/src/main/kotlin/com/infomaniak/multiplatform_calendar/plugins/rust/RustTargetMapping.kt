package com.infomaniak.multiplatform_calendar.plugins.rust

internal fun kotlinTargetNameToRustTriple(targetName: String): String? {
    return when (targetName) {
        "iosArm64" -> "aarch64-apple-ios"
        "iosSimulatorArm64" -> "aarch64-apple-ios-sim"
        "iosX64" -> "x86_64-apple-ios"

        "macosArm64" -> "aarch64-apple-darwin"
        "macosX64" -> "x86_64-apple-darwin"

        "linuxX64" -> "x86_64-unknown-linux-gnu"
        "linuxArm64" -> "aarch64-unknown-linux-gnu"

        "mingwX64" -> "x86_64-pc-windows-gnu"

        else -> null
    }
}

internal fun androidAbiToRustTriple(abi: String): String? {
    return when (abi) {
        "arm64-v8a" -> "aarch64-linux-android"
        "armeabi-v7a" -> "armv7-linux-androideabi"
        "x86" -> "i686-linux-android"
        "x86_64" -> "x86_64-linux-android"
        else -> null
    }
}
