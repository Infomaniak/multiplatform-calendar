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

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Runs `sdkmanager --install ndk;<version>` and streams its output live to the console.
 *
 * Implemented as a [ValueSource] so it can run at configuration time while staying compatible
 * with the configuration cache (the same mechanism `providers.exec` uses internally). Unlike
 * `providers.exec`, it forwards sdkmanager's output as it is produced, so the single-line
 * download/unzip progress bar stays visible during installation.
 */
abstract class NdkInstallValueSource : ValueSource<Int, NdkInstallValueSource.Params> {

    interface Params : ValueSourceParameters {
        val sdkManagerPath: Property<String>
        val sdkRoot: Property<String>
        val ndkVersion: Property<String>
    }

    override fun obtain(): Int {
        val process = ProcessBuilder(
            parameters.sdkManagerPath.get(),
            "--install",
            "ndk;${parameters.ndkVersion.get()}",
        ).apply {
            environment()["ANDROID_SDK_ROOT"] = parameters.sdkRoot.get()
            redirectErrorStream(true)
        }.start()

        // Forward raw bytes (including '\r') so the progress bar updates live on a single line.
        val buffer = ByteArray(1024)
        process.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                System.out.write(buffer, 0, read)
                System.out.flush()
            }
        }

        return process.waitFor()
    }
}

