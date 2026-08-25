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
package com.infomaniak.multiplatform_calendar.core.utils

import com.infomaniak.multiplatform_calendar.core.domain.model.event.ThemedColor
import com.infomaniak.multiplatform_calendar.core.utils.ColorComputation.Companion.from
import com.materialkolor.palettes.TonalPalette

/**
 * Precomputed color roles derived from one calendar/event source color.
 *
 * Naming follows Material-style roles:
 * - `sourceColor` (input) is the base calendar or event color (if the user chose a specific one).
 * - `containerColor` is a 20% alpha variant from the source color. Used for the event background.
 * - `containerVariantColor` is a 10% alpha variant from the source color. Used for the pending event background.
 * - `on*` colors are content colors intended to be used on top of their matching role.
 *
 * Contrast targets:
 * - `onSourceColor` is computed for AA contrast over `sourceColor`.
 * - `onContainerColor` and `onContainerVariantColor` are computed for AAA contrast once containers are
 *   composited on light/dark app surfaces.
 *
 * Instances are cached by source ARGB via [from] so the same source color is computed only once.
 */

internal data class ColorComputation(
    val onSourceColor: ThemedColor,
    val containerColor: Int,
    val onContainerColor: ThemedColor,
    val containerVariantColor: Int,
    val onContainerVariantColor: ThemedColor,
) {
    companion object {
        private const val LIGHT_SURFACE = 0xFFFFFBFE.toInt()
        private const val DARK_SURFACE = 0xFF141218.toInt()
        private const val CONTAINER_ALPHA = 0.25f
        private const val CONTAINER_VARIANT_ALPHA = 0.1f

        private val cache = linkedMapOf<Int, ColorComputation>()
        internal val cacheSize: Int
            get() = cache.size

        fun from(sourceColor: Int): ColorComputation {
            cache[sourceColor]?.let { return it }

            val computed = compute(sourceColor)
            cache[sourceColor] = computed
            return computed
        }

        fun compute(sourceColor: Int): ColorComputation {
            val palette = TonalPalette.fromInt(sourceColor)

            // Since the source color will be used for decorative purposes an AA contrast is sufficient.
            val onSourceColor = ThemedColor(
                light = palette.findColorWithContrast(sourceColor, ContrastType.AA),
                dark = palette.findColorWithContrast(sourceColor, ContrastType.AA),
            )

            val containerColor = sourceColor.withAlpha(CONTAINER_ALPHA)
            val onContainerColor = ThemedColor(
                light = palette.findColorWithContrast(containerColor.compositeOver(LIGHT_SURFACE), ContrastType.AAA),
                dark = palette.findColorWithContrast(containerColor.compositeOver(DARK_SURFACE), ContrastType.AAA),
            )

            val containerVariantColor = sourceColor.withAlpha(CONTAINER_VARIANT_ALPHA)
            val onContainerVariantColor = ThemedColor(
                light = palette.findColorWithContrast(containerVariantColor.compositeOver(LIGHT_SURFACE), ContrastType.AAA),
                dark = palette.findColorWithContrast(containerVariantColor.compositeOver(DARK_SURFACE), ContrastType.AAA),
            )

            return ColorComputation(
                onSourceColor = onSourceColor,
                containerColor = containerColor,
                onContainerColor = onContainerColor,
                containerVariantColor = containerVariantColor,
                onContainerVariantColor = onContainerVariantColor,
            )
        }

        // This function is use by the tests.
        internal fun resetCache() {
            cache.clear()
        }
    }
}

public enum class ContrastType(
    public val value: Double,
    public val contrastDelta: Int,
) {
    AA(4.5, 50),
    AAA(7.0, 66),
}
