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

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import kotlin.test.Test
import kotlin.test.assertTrue

class CalendarColorsTest {

    // Representative spread: dark, mid, light, saturated, near-white, near-black
    private val testColors = listOf(
        0xFF2196F3.toInt(), // Material Blue
        0xFF4CAF50.toInt(), // Green
        0xFFF44336.toInt(), // Red
        0xFF9C27B0.toInt(), // Purple
        0xFFFFEB3B.toInt(), // Yellow (near-white after tint)
        0xFF006400.toInt(), // Dark green
        0xFFFF69B4.toInt(), // Hot pink
        0xFF212121.toInt(), // Near-black
        0xFFE0E0E0.toInt(), // Near-white
    )

    private val lightSurface = 0xFFFFFBFE.toInt()
    private val darkSurface = 0xFF141218.toInt()

    @Test
    fun sourceColor_onSourceColorLight_meetsWcagAa() {
        testColors.forEach { color ->
            val colors = CalendarColors.from(color)
            val contrast = colors.sourceColor.contrastRatioAgainst(colors.onSourceColor.light)
            assertTrue(
                actual = contrast >= 4.5,
                message = "sourceColor #${color.toHex()} vs onSourceColor.light: contrast ${contrast.format2dp()}:1, expected >= 4.5:1",
            )
        }
    }

    @Test
    fun sourceColor_onSourceColorDark_meetsWcagAa() {
        testColors.forEach { color ->
            val colors = CalendarColors.from(color)
            val contrast = colors.sourceColor.contrastRatioAgainst(colors.onSourceColor.dark)
            assertTrue(
                actual = contrast >= 4.5,
                message = "sourceColor #${color.toHex()} vs onSourceColor.dark: contrast ${contrast.format2dp()}:1, expected >= 4.5:1",
            )
        }
    }

    @Test
    fun sourceVariantColor_onSourceVariantColorLight_meetsWcagAaaOverLightSurface() {
        testColors.forEach { color ->
            val colors = CalendarColors.from(color)
            val compositedContainer = colors.sourceVariantColor.compositeOver(lightSurface)
            val contrast = compositedContainer.contrastRatioAgainst(colors.onSourceVariantColor.light)
            assertTrue(
                actual = contrast >= 7.0,
                message = "sourceVariantColor #${color.toHex()} over light surface vs onSourceVariantColor.light: contrast ${contrast.format2dp()}:1, expected >= 7.0:1",
            )
        }
    }

    @Test
    fun sourceVariantColor_onSourceVariantColorDark_meetsWcagAaaOverDarkSurface() {
        testColors.forEach { color ->
            val colors = CalendarColors.from(color)
            val compositedContainer = colors.sourceVariantColor.compositeOver(darkSurface)
            val contrast = compositedContainer.contrastRatioAgainst(colors.onSourceVariantColor.dark)
            assertTrue(
                actual = contrast >= 7.0,
                message = "sourceVariantColor #${color.toHex()} over dark surface vs onSourceVariantColor.dark: contrast ${contrast.format2dp()}:1, expected >= 7.0:1",
            )
        }
    }

    private fun Int.toHex() = toString(16).padStart(8, '0').uppercase()
    private fun Double.format2dp(): String = (kotlin.math.round(this * 100) / 100.0).toString()
}
