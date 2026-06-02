package com.infomaniak.multiplatform_calendar.plugins.rust

import java.io.File

internal fun isWindows(): Boolean {
    return System.getProperty("os.name")
        .lowercase()
        .contains("windows")
}

internal fun exe(name: String): String {
    return if (isWindows()) "$name.exe" else name
}

internal fun currentProcessPath(): String {
    return System.getenv().entries
        .firstOrNull { it.key.equals("PATH", ignoreCase = true) }
        ?.value
        .orEmpty()
}

internal fun pathEnvironmentKey(): String {
    return System.getenv().keys
        .firstOrNull { it.equals("PATH", ignoreCase = true) }
        ?: "PATH"
}

internal fun userHomeDirectory(): File? {
    return System.getProperty("user.home")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
}

internal fun rustupHostTriple(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    val cpu = when {
        arch == "aarch64" || arch == "arm64" -> "aarch64"
        arch.contains("64") -> "x86_64"
        else -> error("Unsupported architecture: $arch")
    }

    return when {
        os.contains("mac") -> "$cpu-apple-darwin"
        os.contains("linux") -> "$cpu-unknown-linux-gnu"
        os.contains("windows") -> "$cpu-pc-windows-msvc"
        else -> error("Unsupported OS: $os")
    }
}
