package com.infomaniak.multiplatform_calendar.plugins.rust

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Executes Cargo")
abstract class CargoExecTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rustInstallationFile: RegularFileProperty

    @get:Internal
    abstract val cargoWorkingDirectory: DirectoryProperty

    @get:Input
    abstract val cargoArguments: ListProperty<String>

    @get:Input
    abstract val environmentVariables: MapProperty<String, String>

    init {
        cargoArguments.convention(emptyList())
        environmentVariables.convention(emptyMap())
    }

    @TaskAction
    fun runCargo() {
        val installation = readRustInstallation(rustInstallationFile.get().asFile)

        execOperations.exec {
            executable = installation.cargoExecutable
            workingDir = cargoWorkingDirectory.get().asFile
            args(cargoArguments.get())

            environment(environmentVariables.get())

            if (installation.isLocal) {
                environment("CARGO_HOME", installation.cargoHome)
                environment("RUSTUP_HOME", installation.rustupHome)
            }

            environment(
                pathEnvironmentKey(),
                installation.cargoBin + File.pathSeparator + currentProcessPath(),
            )
        }
    }
}
