package com.infomaniak.multiplatform_calendar.plugins.rust

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class RustToolchainPlugin : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        val extension = extensions.create("rustToolchain", RustToolchainExtension::class.java)

        extension.rustDirectory.convention(
            rootProject.layout.projectDirectory.dir(".gradle/rust"),
        )

        val bootstrapRust = tasks.register("bootstrapRust", BootstrapRustTask::class.java) {
            rustDirectory.set(extension.rustDirectory)
            toolchain.set(extension.toolchain)
            preferSystemCargo.set(extension.preferSystemCargo)

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

        val targets = detectedKotlinNativeTargets +
                extension.androidTargets.get() +
                extension.extraTargets.get()
        return targets.distinct().sorted()
    }

    private fun Project.detectKotlinNativeRustTargets(): List<String> {
        val kotlinExtension = extensions.findByType(KotlinMultiplatformExtension::class.java)
            ?: return emptyList()

        return kotlinExtension.targets.mapNotNull { target ->
            kotlinTargetNameToRustTriple(target.name)
        }.distinct()
    }
}
