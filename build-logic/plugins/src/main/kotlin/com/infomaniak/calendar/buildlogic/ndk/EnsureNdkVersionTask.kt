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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Verifies that the required NDK version is installed.
 *
 * The actual installation is handled by [EnsureNdkVersionPlugin] at configuration time;
 * this task offers a manual `./gradlew ensureNdkVersion` entry point and an execution-time
 * safety net.
 */
abstract class EnsureNdkVersionTask : DefaultTask() {

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Input
    abstract val sdkDirectoryPath: Property<String>

    init {
        group = "verification"
        description = "Verifies that the required NDK version is installed"
    }

    @TaskAction
    fun verify() {
        val version = ndkVersion.get()
        val ndkDir = File(sdkDirectoryPath.get(), "ndk/$version")

        if (ndkDir.exists()) {
            logger.lifecycle("✓ NDK $version is installed")
        } else {
            throw GradleException("NDK $version is not installed.")
        }
    }
}

