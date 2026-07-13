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
package com.infomaniak.multiplatform_calendar.core.managers

import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.cancellable
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.logFailuresToSentry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

internal suspend inline fun <T> runSdkCall(
    operation: String,
    crossinline block: suspend () -> T,
): T {
    val errorMessage = "Failed to $operation"
    return runCatching {
        block()
    }.cancellable()
        .getOrElse { throwable ->
            throwable.logFailuresToSentry(message = errorMessage)
            throw CalendarSdkException(errorMessage, cause = throwable)
        }
}

internal fun <T> Flow<T>.reportFlowFailures(operation: String): Flow<T> = catch { throwable ->
    // Flow errors are reported but intentionally not propagated to the SDK consumer.
    throwable.logFailuresToSentry(message = "Flow failed to $operation")
}
