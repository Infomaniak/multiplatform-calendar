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
import com.infomaniak.multiplatform_calendar.resources.Res
import com.infomaniak.multiplatform_calendar.resources.color_blue
import com.infomaniak.multiplatform_calendar.resources.color_brown
import com.infomaniak.multiplatform_calendar.resources.color_default
import com.infomaniak.multiplatform_calendar.resources.color_emerald
import com.infomaniak.multiplatform_calendar.resources.color_gray
import com.infomaniak.multiplatform_calendar.resources.color_lime
import com.infomaniak.multiplatform_calendar.resources.color_magenta
import com.infomaniak.multiplatform_calendar.resources.color_orange
import com.infomaniak.multiplatform_calendar.resources.color_purple
import com.infomaniak.multiplatform_calendar.resources.color_yellow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

internal suspend fun localizedColorChoices(calendarColor: Int): List<NamedColor> = buildList {
    add(NamedColor(sourceColor = calendarColor, name = getString(Res.string.color_default)))
    PresetColor.entries.mapTo(this) { it.toNamedColor() }
}

private enum class PresetColor(val argb: Long, val nameResource: StringResource) {
    Orange(0xFFF97207, Res.string.color_orange),
    Yellow(0xFFECB306, Res.string.color_yellow),
    Lime(0xFF83BB03, Res.string.color_lime),
    Emerald(0xFF23A662, Res.string.color_emerald),
    Blue(0xFF0C93EB, Res.string.color_blue),
    Purple(0xFF9346FF, Res.string.color_purple),
    Magenta(0xFFD734CC, Res.string.color_magenta),
    Brown(0xFFB87138, Res.string.color_brown),
    Gray(0xFF62748E, Res.string.color_gray);

    suspend fun toNamedColor(): NamedColor = NamedColor(sourceColor = argb.toInt(), name = getString(nameResource))
}
