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

/** DSL of [EnsureNdkVersionPlugin], registered under the `ensureNdkVersion` name. */
abstract class EnsureNdkVersionExtension {

    /**
     * Minimum acceptable NDK version, e.g. `"30.0.14904198"`.
     *
     * It is a *minimum*, not a pin: a newer installed NDK satisfies it and is reused.
     */
    abstract val minimumVersion: Property<String>

    /**
     * The NDK version that will actually be used, wired by [EnsureNdkVersionPlugin].
     *
     * Resolving it is what triggers the install when no installed NDK satisfies
     * [minimumVersion], so it must be plugged into whatever consumes the NDK — here
     * `cargo.ndkVersion` — rather than repeating [minimumVersion] there.
     */
    abstract val resolvedVersion: Property<String>
}
