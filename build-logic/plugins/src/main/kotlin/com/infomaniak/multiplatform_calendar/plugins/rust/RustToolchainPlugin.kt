package com.infomaniak.multiplatform_calendar.plugins.rust

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

class RustToolchainPlugin : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        val extension = extensions.create(
            "rustToolchain",
            RustToolchainExtension::class.java,
        )

        extension.rustDirectory.convention(
            rootProject.layout.projectDirectory.dir(".gradle/rust"),
        )

        /*
         * Important:
         * Shims must exist during Gradle configuration/sync because Gobley may
         * need a Cargo path before any task can run.
         *
         * This first generation uses convention values. The shims are regenerated
         * after project evaluation with the final user configuration.
         */
        writeRustShimsIfNeeded(extension)

        val bootstrapRust = tasks.register(
            "bootstrapRust",
            BootstrapRustTask::class.java,
        ) {
            rustDirectory.set(extension.rustDirectory)
            toolchain.set(extension.toolchain)

            /*
             * With LOCAL_THEN_SYSTEM, real builds should install and use the
             * project-local toolchain.
             *
             * The system Cargo fallback is mainly for Gradle sync/configuration,
             * before bootstrapRust has had a chance to run.
             */
            preferSystemCargo.set(
                providers.provider {
                    shouldBootstrapPreferSystemCargo(extension.resolutionStrategy.get())
                },
            )

            rustTargets.set(
                providers.provider {
                    resolveRustTargets(extension)
                },
            )

            installationFile.set(
                layout.buildDirectory.file("rustToolchain/resolved.properties"),
            )
        }

        tasks.withType(CargoExecTask::class.java).configureEach {
            dependsOn(bootstrapRust)

            rustInstallationFile.set(
                bootstrapRust.flatMap { it.installationFile },
            )

            cargoWorkingDirectory.convention(layout.projectDirectory)
        }

        configureGobleyRustIfAvailable(extension)

        wireBootstrapRustIntoBuildLifecycle(
            extension = extension,
            bootstrapRust = bootstrapRust,
        )

        afterEvaluate {
            writeRustShimsIfNeeded(extension)
            configureGobleyRustIfAvailable(extension)
        }
    }

    private fun Project.writeRustShimsIfNeeded(
        extension: RustToolchainExtension,
    ) {
        if (!extension.generateShimsAtConfigurationTime.get()) {
            return
        }

        val rustDirectory = extension.rustDirectory.get().asFile
        val strategy = extension.resolutionStrategy.get()

        writeRustShims(
            rustDirectory = rustDirectory,
            strategy = strategy,
            toolchain = extension.toolchain.get(),
        )

        logger.lifecycle(
            "Generated Rust shims in ${File(rustDirectory, "shims/bin").absolutePath} with strategy $strategy",
        )
    }

    private fun shouldBootstrapPreferSystemCargo(
        strategy: RustToolchainResolutionStrategy,
    ): Boolean {
        return when (strategy) {
            RustToolchainResolutionStrategy.LOCAL_THEN_SYSTEM -> false
            RustToolchainResolutionStrategy.LOCAL_ONLY -> false
            RustToolchainResolutionStrategy.SYSTEM_THEN_LOCAL -> true
            RustToolchainResolutionStrategy.SYSTEM_ONLY -> true
        }
    }

    private fun Project.configureGobleyRustIfAvailable(
        extension: RustToolchainExtension,
    ) {
        pluginManager.withPlugin("dev.gobley.rust") {
            if (!extension.configureGobleyRust.get()) {
                return@withPlugin
            }

            val shimBinDirectory = File(
                extension.rustDirectory.get().asFile,
                "shims/bin",
            )

            val gobleyRustExtension = extensions.findByName("rust")
                ?: error(
                    "Gobley Rust plugin is applied, but the 'rust' extension was not found.",
                )

            setGobleyToolchainDirectory(
                gobleyRustExtension = gobleyRustExtension,
                directory = shimBinDirectory,
            )

            logger.lifecycle(
                "Configured Gobley Rust toolchain directory: ${shimBinDirectory.absolutePath}",
            )
        }
    }

    private fun setGobleyToolchainDirectory(
        gobleyRustExtension: Any,
        directory: File,
    ) {
        val setter = gobleyRustExtension.javaClass.methods.firstOrNull { method ->
            method.name == "setToolchainDirectory" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0].isAssignableFrom(File::class.java)
        }

        if (setter != null) {
            setter.invoke(gobleyRustExtension, directory)
            return
        }

        error(
            "Unable to configure Gobley rust.toolchainDirectory. " +
                    "The Gobley Rust extension does not expose setToolchainDirectory(File).",
        )
    }

    private fun Project.wireBootstrapRustIntoBuildLifecycle(
        extension: RustToolchainExtension,
        bootstrapRust: TaskProvider<BootstrapRustTask>,
    ) {
        tasks.configureEach {
            if (name == bootstrapRust.name) return@configureEach
            if (name == "clean") return@configureEach

            val shouldWireAndroidPreBuild =
                extension.wireIntoAndroidPreBuild.get() &&
                        isAndroidPreBuildTaskName(name)

            val shouldWireRustConsumer =
                extension.wireIntoRustConsumerTasks.get() &&
                        isRustConsumerTaskName(name)

            if (shouldWireAndroidPreBuild || shouldWireRustConsumer) {
                dependsOn(bootstrapRust)
            }

            if (shouldWireRustConsumer && this is Exec) {
                configureExecTaskWithRustToolchain(
                    bootstrapRust = bootstrapRust,
                )
            }
        }
    }

    private fun Exec.configureExecTaskWithRustToolchain(
        bootstrapRust: TaskProvider<BootstrapRustTask>,
    ) {
        doFirst("configureRustToolchainEnvironment") {
            val execTask = this as Exec

            val installationFile = bootstrapRust
                .get()
                .installationFile
                .get()
                .asFile

            val installation = readRustInstallation(installationFile)

            val pathKey = pathEnvironmentKey()
            val currentPath = execTask.environment[pathKey]?.toString()
                ?: currentProcessPath()

            execTask.environment(
                pathKey,
                installation.cargoBin + File.pathSeparator + currentPath,
            )

            if (installation.isLocal) {
                installation.cargoHome?.let { cargoHome ->
                    execTask.environment("CARGO_HOME", cargoHome)
                }

                installation.rustupHome?.let { rustupHome ->
                    execTask.environment("RUSTUP_HOME", rustupHome)
                }
            }

            val currentExecutable = execTask.executable

            if (
                currentExecutable == null ||
                currentExecutable == "cargo" ||
                File(currentExecutable).name == exe("cargo")
            ) {
                execTask.executable = installation.cargoExecutable
            }
        }
    }

    private fun isAndroidPreBuildTaskName(taskName: String): Boolean {
        return taskName == "preBuild" || taskName.endsWith("PreBuild")
    }

    private fun isRustConsumerTaskName(taskName: String): Boolean {
        val normalizedName = taskName.lowercase()

        if (normalizedName == "bootstraprust") {
            return false
        }

        return normalizedName.contains("cargo") ||
                normalizedName.contains("uniffi") ||
                normalizedName.contains("rust")
    }

    private fun Project.resolveRustTargets(
        extension: RustToolchainExtension,
    ): List<String> {
        if (!extension.installRustTargets.get()) {
            return emptyList()
        }

        val detectedKotlinNativeTargets =
            if (extension.autoDetectKotlinNativeTargets.get()) {
                detectKotlinNativeRustTargets()
            } else {
                emptyList()
            }

        return (
                detectedKotlinNativeTargets +
                        extension.androidTargets.get() +
                        extension.extraTargets.get()
                )
            .distinct()
            .sorted()
    }

    private fun Project.detectKotlinNativeRustTargets(): List<String> {
        val kotlinExtension = extensions.findByType(KotlinMultiplatformExtension::class.java)
            ?: return emptyList()

        return kotlinExtension.targets
            .mapNotNull { target ->
                kotlinTargetNameToRustTriple(target.name)
            }
            .distinct()
    }
}
