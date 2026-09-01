/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.calendar.buildlogic.ndk

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Convention plugin that makes sure an NDK at least as recent as `ensureNdkVersion.minimumVersion`
 * is available, installing it through `sdkmanager` when it is not.
 *
 * The declared version is a **minimum**: a newer installed NDK satisfies it and is reused, and
 * [EnsureNdkVersionExtension.resolvedVersion] then points at that one. Only when no installed NDK
 * qualifies is the declared version downloaded.
 *
 * Unlike its pre-AGP-9 ancestor, this plugin no longer reads `android.ndkVersion`: the
 * `com.android.kotlin.multiplatform.library` extension does not expose one, and AGP 9 removed it
 * from `CommonExtension`. The version is therefore declared here and forwarded explicitly to the
 * component that cross-compiles the Rust code:
 *
 * ```kotlin
 * ensureNdkVersion {
 *     minimumVersion = "30.0.14904198"
 * }
 *
 * cargo {
 *     ndkVersion = ensureNdkVersion.resolvedVersion
 * }
 * ```
 *
 * Wiring `resolvedVersion` is what makes the check effective: `ch.ubique.uniffi.plugin` silently
 * falls back to the newest installed NDK when the requested one is missing, so a missing NDK would
 * otherwise produce a green build against an unintended — possibly pre-16 KB-page — toolchain.
 */
class EnsureNdkVersionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, EnsureNdkVersionExtension::class.java)

        val providers = target.providers
        val sdkDirectory = target.androidSdkDirectory()

        // Lazy on purpose: resolution (and the potential install) only happens once something
        // actually queries the NDK version, not on every configuration phase.
        extension.resolvedVersion.set(
            extension.minimumVersion.map { minimum -> resolve(minimum, sdkDirectory.get(), providers) }
        )
        extension.resolvedVersion.disallowChanges()

        target.tasks.register<EnsureNdkVersionTask>(TASK_NAME) {
            ndkVersion.set(extension.resolvedVersion)
            sdkDirectoryPath.set(sdkDirectory.map(File::getAbsolutePath))
        }
    }

    private fun resolve(minimum: String, sdkDirectory: File, providers: ProviderFactory): String {
        val newestSatisfying = installedNdkVersions(sdkDirectory)
            .filter { compareNdkVersions(it, minimum) >= 0 }
            .maxWithOrNull(::compareNdkVersions)

        return when (newestSatisfying) {
            null -> {
                installNdk(minimum, sdkDirectory, providers)
                minimum
            }
            minimum -> minimum
            else -> newestSatisfying
        }
    }

    private fun installNdk(version: String, sdkDirectory: File, providers: ProviderFactory) {
        val sdkManager = findSdkManager(sdkDirectory) ?: throw GradleException(sdkManagerNotFoundMessage(version))

        println("⚠️ NDK $version not found. Installing via sdkmanager (this may take a few minutes)...")

        val exitCode = providers.of(NdkInstallValueSource::class.java) {
            parameters.sdkManagerPath.set(sdkManager.absolutePath)
            parameters.sdkRoot.set(sdkDirectory.absolutePath)
            parameters.ndkVersion.set(version)
        }.get()

        if (exitCode != 0) throw GradleException("Failed to install NDK $version (exit code: $exitCode)")

        // The exit code alone cannot be trusted: the `android` CLI that now backs the deprecated
        // `sdkmanager` reports a success even for a package that does not exist. Only the presence
        // of the directory proves the install went through.
        if (version !in installedNdkVersions(sdkDirectory)) {
            throw GradleException(
                "sdkmanager reported a success but NDK $version is still not installed. " +
                    "Check that this version exists in the SDK repository."
            )
        }

        println("✓ NDK $version installed successfully")
    }

    /**
     * AGP used to expose the SDK location through its extension, which AGP 9 no longer does for
     * KMP library modules, so resolve it the same way the Android tooling does.
     */
    private fun Project.androidSdkDirectory(): Provider<File> {
        val localProperties = rootProject.layout.projectDirectory.file(LOCAL_PROPERTIES)
        return providers.environmentVariable("ANDROID_HOME")
            .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
            .orElse(
                providers.fileContents(localProperties).asText.map { text ->
                    text.lineSequence()
                        .map(String::trim)
                        .firstOrNull { it.startsWith("$SDK_DIR_KEY=") }
                        ?.substringAfter('=')
                        // local.properties is a Java properties file: ':' and '\' are escaped.
                        ?.replace("\\:", ":")
                        ?.replace("\\\\", "\\")
                        ?: ""
                }
            )
            .map { path ->
                if (path.isEmpty()) throw GradleException(sdkNotFoundMessage())
                File(path)
            }
    }

    /** Lists the fully installed (side-by-side) NDK versions under `<sdk>/ndk`. */
    private fun installedNdkVersions(sdkDirectory: File): List<String> {
        val ndkRoot = File(sdkDirectory, "ndk")
        return (ndkRoot.listFiles { file -> file.isDirectory } ?: emptyArray())
            .filter { File(it, "source.properties").exists() }
            .map { it.name }
    }

    /** Compares two dotted numeric NDK versions, e.g. "30.0.14904198" vs "29.0.14206865". */
    private fun compareNdkVersions(left: String, right: String): Int {
        val leftParts = left.split('.')
        val rightParts = right.split('.')
        repeat(maxOf(leftParts.size, rightParts.size)) { index ->
            val l = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
            val r = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun findSdkManager(sdkDirectory: File): File? = listOf(
        "cmdline-tools/latest/bin/sdkmanager",
        "cmdline-tools/bin/sdkmanager",
        "tools/bin/sdkmanager",
    ).map { File(sdkDirectory, it) }.firstOrNull { it.exists() }

    private fun sdkNotFoundMessage(): String = """
        Unable to resolve the Android SDK directory.
        Set ANDROID_HOME (or ANDROID_SDK_ROOT), or add "$SDK_DIR_KEY" to $LOCAL_PROPERTIES.
    """.trimIndent()

    private fun sdkManagerNotFoundMessage(version: String): String = """

        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        ⚠️  NDK $version is missing and 'sdkmanager' was not found, so it cannot
        be installed automatically.

        Install the Android SDK Command-line Tools via Android Studio:
           Settings > Android SDK > SDK Tools > Android SDK Command-line Tools

        (Or install NDK $version directly from the SDK Manager.)
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    """.trimIndent()

    private companion object {
        const val EXTENSION_NAME = "ensureNdkVersion"
        const val TASK_NAME = "ensureNdkVersion"
        const val LOCAL_PROPERTIES = "local.properties"
        const val SDK_DIR_KEY = "sdk.dir"
    }
}
