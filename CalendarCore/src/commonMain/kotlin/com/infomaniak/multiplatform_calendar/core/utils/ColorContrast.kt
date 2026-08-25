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

import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


/** Returns a palette tone color (ARGB Int) that reaches the specified [contrast] against [background]. */
internal fun TonalPalette.findColorWithContrast(background: Int, contrast: ContrastType): Int {
    val backgroundTone = Hct.fromInt(background).tone.toInt()
    val isBackgroundLight = backgroundTone >= 50
    val resultTone = (if (isBackgroundLight) {
        backgroundTone - contrast.contrastDelta
    } else {
        backgroundTone + contrast.contrastDelta
    }).coerceIn(0, 100)
    return tone(resultTone)
}

public fun Int.withAlpha(alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return (this and 0x00FFFFFF) or (a shl 24)
}

/** Composites this foreground ARGB over [background] ARGB. */
public fun Int.compositeOver(background: Int): Int {
    val fgA = ((this ushr 24) and 0xFF) / 255.0
    val bgA = ((background ushr 24) and 0xFF) / 255.0

    val outA = fgA + bgA * (1.0 - fgA)
    if (outA <= 0.0) return 0

    val fgR = (this ushr 16) and 0xFF
    val fgG = (this ushr 8) and 0xFF
    val fgB = this and 0xFF

    val bgR = (background ushr 16) and 0xFF
    val bgG = (background ushr 8) and 0xFF
    val bgB = background and 0xFF

    fun compositeChannel(fg: Int, bg: Int): Int {
        val out = ((fg * fgA) + (bg * bgA * (1.0 - fgA))) / outA
        return out.toInt().coerceIn(0, 255)
    }

    val outR = compositeChannel(fgR, bgR)
    val outG = compositeChannel(fgG, bgG)
    val outB = compositeChannel(fgB, bgB)
    val outAlpha = (outA * 255.0 + 0.5).toInt().coerceIn(0, 255)

    return (outAlpha shl 24) or (outR shl 16) or (outG shl 8) or outB
}

internal fun Int.contrastRatioAgainst(other: Int): Double {
    val l1 = luminance()
    val l2 = other.luminance()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Int.luminance(): Double {
    fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    val r = channel((this ushr 16) and 0xFF)
    val g = channel((this ushr 8) and 0xFF)
    val b = channel(this and 0xFF)

    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
