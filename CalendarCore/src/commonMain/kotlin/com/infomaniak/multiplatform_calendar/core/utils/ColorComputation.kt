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
import com.materialkolor.palettes.TonalPalette

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
        private const val WCAG_AAA_CONTRAST = 7.0
        private const val WCAG_AA_CONTRAST = 4.5

        private val cache = linkedMapOf<Int, ColorComputation>()

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
                light = palette.findToneWithContrast(sourceColor, WCAG_AA_CONTRAST),
                dark = palette.findToneWithContrast(sourceColor, WCAG_AA_CONTRAST),
            )

            val containerColor = sourceColor.withAlpha(0.2f)
            val onContainerColor = ThemedColor(
                light = palette.findToneWithContrast(containerColor.compositeOver(LIGHT_SURFACE), WCAG_AAA_CONTRAST),
                dark = palette.findToneWithContrast(containerColor.compositeOver(DARK_SURFACE), WCAG_AAA_CONTRAST),
            )

            val containerVariantColor = sourceColor.withAlpha(0.1f)
            val onContainerVariantColor = ThemedColor(
                light = palette.findToneWithContrast(containerVariantColor.compositeOver(LIGHT_SURFACE), WCAG_AAA_CONTRAST),
                dark = palette.findToneWithContrast(containerVariantColor.compositeOver(DARK_SURFACE), WCAG_AAA_CONTRAST),
            )

            return ColorComputation(
                onSourceColor = onSourceColor,
                containerColor = containerColor,
                onContainerColor = onContainerColor,
                containerVariantColor = containerVariantColor,
                onContainerVariantColor = onContainerVariantColor,
            )
        }
    }

}
