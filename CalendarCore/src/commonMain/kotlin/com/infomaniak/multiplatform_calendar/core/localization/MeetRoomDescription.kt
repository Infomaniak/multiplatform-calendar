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

import com.infomaniak.multiplatform_calendar.core.extensions.markedBlock
import com.infomaniak.multiplatform_calendar.resources.Res
import com.infomaniak.multiplatform_calendar.resources.meet_room_description_do_not_modify
import com.infomaniak.multiplatform_calendar.resources.meet_room_description_join
import org.jetbrains.compose.resources.getString

internal suspend fun meetRoomDescriptionBlock(url: String): String = markedBlock(
    "${getString(Res.string.meet_room_description_do_not_modify)}\n\n${getString(Res.string.meet_room_description_join, url)}",
)
