package com.infomaniak.multiplatform_calendar.plugins.rust

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.Properties
import javax.inject.Inject

@DisableCachingByDefault(because = "Bootstraps an external Rust toolchain")
abstract class BootstrapRustTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:Internal
    abstract val rustDirectory: DirectoryProperty

    @get:Input
    abstract val toolchain: Property<String>

    @get:Input
    abstract val preferSystemCargo: Property<Boolean>

    @get:Input
    abstract val rustTargets: ListProperty<String>

    @get:OutputFile
    abstract val installationFile: RegularFileProperty

    @TaskAction
    fun bootstrap() {
        if (preferSystemCargo.get()) {
            val systemCargo = findExistingTool("cargo")

            if (systemCargo != null) {
                logger.lifecycle("Using existing cargo: ${systemCargo.absolutePath}")

                val systemRustup = findExistingTool("rustup")

                ensureRustTargets(
                    rustup = systemRustup,
                    environment = emptyMap(),
                    isLocal = false,
                )

                writeInstallation(
                    cargoExecutable = systemCargo,
                    isLocal = false,
                )

                return
            }
        }

        val localCargo = installLocalRustIfNeeded()

        ensureRustTargets(
            rustup = localRustupFile(),
            environment = localRustEnvironment(),
            isLocal = true,
        )

        writeInstallation(
            cargoExecutable = localCargo,
            isLocal = true,
        )
    }

    private fun installLocalRustIfNeeded(): File {
        val cargo = localCargoFile()

        if (canRun(cargo, localRustEnvironment(), "--version")) {
            logger.lifecycle("Using local cargo: ${cargo.absolutePath}")
            return cargo
        }

        val installerName = exe("rustup-init")
        val installer = rustDirectory.get()
            .dir("installer")
            .file(installerName)
            .asFile

        installer.parentFile.mkdirs()

        val triple = rustupHostTriple()
        val url = "https://static.rust-lang.org/rustup/dist/$triple/$installerName"

        logger.lifecycle("Downloading rustup from $url")

        URI(url).toURL().openStream().use { input ->
            installer.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (!isWindows()) {
            installer.setExecutable(true)
        }

        logger.lifecycle("Installing local Rust toolchain '${toolchain.get()}'")

        execOperations.exec {
            executable = installer.absolutePath
            args(
                "-y",
                "--no-modify-path",
                "--profile",
                "minimal",
                "--default-toolchain",
                toolchain.get(),
            )
            environment(localRustEnvironment())
        }

        if (!canRun(cargo, localRustEnvironment(), "--version")) {
            error("Local cargo was installed but cannot be executed: ${cargo.absolutePath}")
        }

        return cargo
    }

    private fun ensureRustTargets(
        rustup: File?,
        environment: Map<String, String>,
        isLocal: Boolean,
    ) {
        val targets = rustTargets.get()

        if (targets.isEmpty()) {
            return
        }

        if (rustup == null || !canRun(rustup, environment, "--version")) {
            logger.warn(
                "Rust targets were requested but rustup was not found. " +
                        "Targets will not be installed automatically: $targets",
            )
            return
        }

        logger.lifecycle("Ensuring Rust targets: $targets")

        execOperations.exec {
            executable = rustup.absolutePath
            args("target", "add")

            if (isLocal) {
                args("--toolchain", toolchain.get())
            }

            args(targets)

            environment(environment)
        }
    }

    private fun writeInstallation(
        cargoExecutable: File,
        isLocal: Boolean,
    ) {
        val output = installationFile.get().asFile
        output.parentFile.mkdirs()

        val cargoBin = cargoExecutable.parentFile.absolutePath

        val properties = Properties().apply {
            setProperty("cargoExecutable", cargoExecutable.absolutePath)
            setProperty("cargoBin", cargoBin)
            setProperty("local", isLocal.toString())

            if (isLocal) {
                setProperty("cargoHome", cargoHomeDirectory().absolutePath)
                setProperty("rustupHome", rustupHomeDirectory().absolutePath)
            }
        }

        output.outputStream().use { stream ->
            properties.store(stream, "Generated by bootstrapRust")
        }
    }

    private fun findExistingTool(name: String): File? {
        val executableName = exe(name)

        val candidates = buildList {
            currentProcessPath()
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
                .mapTo(this) { File(it, executableName) }

            System.getenv("CARGO_HOME")
                ?.takeIf { it.isNotBlank() }
                ?.let { cargoHome ->
                    add(File(cargoHome, "bin/$executableName"))
                }

            userHomeDirectory()?.let { home ->
                add(File(home, ".cargo/bin/$executableName"))
            }
        }

        return candidates
            .distinctBy { it.absolutePath }
            .firstOrNull { candidate ->
                candidate.isFile && canRun(candidate, emptyMap(), "--version")
            }
    }

    private fun canRun(
        executableFile: File,
        environment: Map<String, String>,
        vararg arguments: String,
    ): Boolean {
        if (!executableFile.isFile) {
            return false
        }

        return try {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()

            val result = execOperations.exec {
                executable = executableFile.absolutePath
                args(arguments.toList())
                standardOutput = stdout
                errorOutput = stderr
                isIgnoreExitValue = true
                environment(environment)
            }

            result.exitValue == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun localRustEnvironment(): Map<String, String> {
        return mapOf(
            "CARGO_HOME" to cargoHomeDirectory().absolutePath,
            "RUSTUP_HOME" to rustupHomeDirectory().absolutePath,
            pathEnvironmentKey() to localCargoBinDirectory().absolutePath + File.pathSeparator + currentProcessPath(),
        )
    }

    private fun rustDirectoryFile(): File {
        return rustDirectory.get().asFile
    }

    private fun cargoHomeDirectory(): File {
        return File(rustDirectoryFile(), "cargo")
    }

    private fun rustupHomeDirectory(): File {
        return File(rustDirectoryFile(), "rustup")
    }

    private fun localCargoBinDirectory(): File {
        return File(cargoHomeDirectory(), "bin")
    }

    private fun localCargoFile(): File {
        return File(localCargoBinDirectory(), exe("cargo"))
    }

    private fun localRustupFile(): File {
        return File(localCargoBinDirectory(), exe("rustup"))
    }
}
