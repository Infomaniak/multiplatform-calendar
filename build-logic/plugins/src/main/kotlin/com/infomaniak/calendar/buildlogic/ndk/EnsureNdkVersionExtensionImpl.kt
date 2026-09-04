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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * Concrete implementation of [EnsureNdkVersionExtension], registered by [EnsureNdkVersionPlugin]
 * as the instance type behind the public [EnsureNdkVersionExtension] interface.
 *
 * The backing [Property] for `resolvedVersion` lives here, private, and can only be written
 * through [setResolvedVersion]. Build scripts only ever see the interface's read-only
 * [Provider], so they can depend on the resolved version but never overwrite it.
 */
internal abstract class EnsureNdkVersionExtensionImpl @Inject constructor(
    objects: ObjectFactory,
) : EnsureNdkVersionExtension {

    private val resolvedVersionProperty: Property<String> = objects.property(String::class.java)

    override val resolvedVersion: Provider<String> get() = resolvedVersionProperty

    /** Only [EnsureNdkVersionPlugin] should call this, once, right after creating the extension. */
    internal fun setResolvedVersion(provider: Provider<String>) {
        resolvedVersionProperty.set(provider)
        resolvedVersionProperty.disallowChanges()
    }
}
