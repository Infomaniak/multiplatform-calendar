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
package com.infomaniak.multiplatform_calendar.meeting_rooms.data.local

import com.infomaniak.multiplatform_calendar.meeting_rooms.data.local.dao.MeetingRoomDao
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Tables of the meeting rooms module, to declare in the host Room database. */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public interface MeetingRoomsDatabase {
    public fun meetingRoomDao(): MeetingRoomDao
}
