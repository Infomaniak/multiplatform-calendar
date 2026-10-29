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
package com.infomaniak.multiplatform_calendar.meet.utils

import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetErrorCause
import com.infomaniak.multiplatform_calendar.meet.domain.model.exceptions.MeetException
import com.infomaniak.multiplatform_core.crashreport.CrashReport
import com.infomaniak.multiplatform_core.crashreport.FailureKind
import com.infomaniak.multiplatform_core.crashreport.ModuleCaller
import kotlinx.io.IOException

internal class MeetCaller(crashReport: CrashReport) : ModuleCaller<MeetException>(crashReport, errorMessage = "Meet call failed") {

    override fun kindOf(failure: Throwable): FailureKind = when (failure) {
        is MeetException -> FailureKind.Handled
        is IOException -> FailureKind.Network
        else -> FailureKind.Unexpected
    }

    override fun convert(failure: Exception): MeetException = when (failure) {
        is MeetException -> failure
        is IOException -> MeetException(MeetErrorCause.Network, failure)
        else -> MeetException(MeetErrorCause.Unknown(failure), failure)
    }
}
