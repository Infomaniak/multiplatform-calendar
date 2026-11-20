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
package com.infomaniak.multiplatform_calendar.core.localization

import com.infomaniak.multiplatform_calendar.core.domain.model.color.NamedColor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorChoicesTest {

    @Test
    fun localizedColorChoices_leadsWithCalendarColorThenPresets() = runTest {
        val expected = listOf(
            NamedColor(CALENDAR_COLOR, "Default"),
            NamedColor(0xFFF97207.toInt(), "Orange"),
            NamedColor(0xFFECB306.toInt(), "Yellow"),
            NamedColor(0xFF83BB03.toInt(), "Lime"),
            NamedColor(0xFF23A662.toInt(), "Emerald"),
            NamedColor(0xFF0C93EB.toInt(), "Blue"),
            NamedColor(0xFF9346FF.toInt(), "Purple"),
            NamedColor(0xFFD734CC.toInt(), "Magenta"),
            NamedColor(0xFFB87138.toInt(), "Brown"),
            NamedColor(0xFF62748E.toInt(), "Gray"),
        )

        withPreferredLanguage("en") { assertEquals(expected, localizedColorChoices(CALENDAR_COLOR)) }
    }

    @Test
    fun localizedColorChoices_translatesNames() = runTest {
        val expected = listOf("Défaut", "Orange", "Jaune", "Citron vert", "Émeraude", "Bleu", "Violet", "Magenta", "Marron", "Gris")

        withPreferredLanguage("fr") { assertEquals(expected, localizedColorChoices(CALENDAR_COLOR).map { it.name }) }
    }

    private companion object {
        const val CALENDAR_COLOR = 0xFF2196F3.toInt()
    }
}
