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

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Convention plugin that makes sure an NDK at least as recent as `android.ndkVersion` is
 * available before the build proceeds.
 *
 * `android.ndkVersion` is treated as a **minimum**: if a newer (or equal) NDK is already
 * installed it is reused (and `android.ndkVersion` is repointed to it so AGP/Gobley use the
 * installed one); only when every installed NDK is older — or none is installed — is the
 * declared version downloaded via `sdkmanager`.
 *
 * The check runs during configuration (before the Gobley Cargo plugin reads the NDK
 * directories), so a fresh machine — including an IDE Gradle sync — builds without any
 * manual setup.
 *
 * Usage in build.gradle.kts:
 * ```kotlin
 * plugins {
 *     id("ensure-ndk-version")
 * }
 * ```
 */
class EnsureNdkVersionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.plugins.withId(ANDROID_LIBRARY_PLUGIN_ID) {
            target.afterEvaluate(::configureNdkVersion)
        }
    }

    private fun configureNdkVersion(project: Project) {
        val android = project.extensions.getByName(ANDROID_EXTENSION_NAME) as CommonExtension<*, *, *, *, *, *>
        val minVersion = android.ndkVersion.takeIf { it.isNotEmpty() } ?: return
        val sdkDirectory = android.resolveSdkDirectory()
            ?: throw GradleException("Unable to resolve Android SDK directory from AGP. Configure the SDK location (e.g., ANDROID_SDK_ROOT or local.properties sdk.dir) and re-run the build.")

        // Treat the declared version as a minimum: reuse the newest installed NDK that is
        // >= it, and only install when none qualifies.
        val newestSatisfying = installedNdkVersions(sdkDirectory)
            .filter { compareNdkVersions(it, minVersion) >= 0 }
            .maxWithOrNull(::compareNdkVersions)

        val effectiveVersion = when {
            newestSatisfying == null -> {
                installNdk(project, minVersion, sdkDirectory)
                minVersion
            }
            newestSatisfying == minVersion -> {
                project.logger.quiet("✓ NDK $minVersion is configured")
                minVersion
            }
            else -> {
                project.logger.quiet("✓ NDK $newestSatisfying is installed and satisfies the required minimum $minVersion")
                newestSatisfying
            }
        }

        // AGP and the Gobley Cargo plugin read android.ndkVersion, so point them at the NDK we
        // actually resolved (otherwise they would look for the exact declared version).
        if (effectiveVersion != minVersion) android.ndkVersion = effectiveVersion

        project.tasks.register<EnsureNdkVersionTask>(TASK_NAME) {
            ndkVersion.set(effectiveVersion)
            sdkDirectoryPath.set(sdkDirectory.absolutePath)
        }
    }

    private fun installNdk(project: Project, version: String, sdkDirectory: File) {
        val sdkManager = findSdkManager(sdkDirectory)
            ?: throw GradleException(sdkManagerNotFoundMessage(version))

        project.logger.lifecycle("⚠️ NDK $version not found. Installing via sdkmanager (this may take a few minutes)...")

        val exitCode = project.providers.of(NdkInstallValueSource::class.java) {
            parameters.sdkManagerPath.set(sdkManager.absolutePath)
            parameters.sdkRoot.set(sdkDirectory.absolutePath)
            parameters.ndkVersion.set(version)
        }.get()

        if (exitCode != 0) throw GradleException("Failed to install NDK $version (exit code: $exitCode)")

        project.logger.lifecycle("✓ NDK $version installed successfully")
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

    /**
     * [CommonExtension] exposes `ndkVersion` but not `sdkDirectory`, so we read the latter
     * reflectively from the concrete Android extension implementation.
     */
    private fun CommonExtension<*, *, *, *, *, *>.resolveSdkDirectory(): File? {
        return when (val value = javaClass.getMethod("getSdkDirectory").invoke(this)) {
            is File -> value
            is Directory -> value.asFile
            is Provider<*> -> (value.get() as? Directory)?.asFile
            else -> null
        }
    }

    private fun findSdkManager(sdkDirectory: File): File? = listOf(
        "cmdline-tools/latest/bin/sdkmanager",
        "cmdline-tools/bin/sdkmanager",
        "tools/bin/sdkmanager",
    ).map { File(sdkDirectory, it) }.firstOrNull { it.exists() }

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
        const val ANDROID_LIBRARY_PLUGIN_ID = "com.android.library"
        const val ANDROID_EXTENSION_NAME = "android"
        const val TASK_NAME = "ensureNdkVersion"
    }
}






