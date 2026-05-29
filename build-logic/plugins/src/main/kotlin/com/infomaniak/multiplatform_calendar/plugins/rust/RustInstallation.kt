package com.infomaniak.multiplatform_calendar.plugins.rust

import java.io.File
import java.util.Properties

internal data class RustInstallation(
    val cargoExecutable: String,
    val cargoBin: String,
    val isLocal: Boolean,
    val cargoHome: String?,
    val rustupHome: String?,
)

internal fun readRustInstallation(file: File): RustInstallation {
    val properties = Properties()

    file.inputStream().use(properties::load)

    fun required(name: String): String {
        return properties.getProperty(name)
            ?: error("Missing '$name' in ${file.absolutePath}")
    }

    return RustInstallation(
        cargoExecutable = required("cargoExecutable"),
        cargoBin = required("cargoBin"),
        isLocal = required("local").toBoolean(),
        cargoHome = properties.getProperty("cargoHome"),
        rustupHome = properties.getProperty("rustupHome"),
    )
}
