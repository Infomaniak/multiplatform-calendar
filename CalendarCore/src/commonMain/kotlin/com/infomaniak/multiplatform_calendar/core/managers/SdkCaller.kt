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

import com.infomaniak.multiplatform_calendar.core.data.CrashReport
import com.infomaniak.multiplatform_calendar.core.data.CrashReportLevel
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.cancellable
import com.infomaniak.multiplatform_calendar.data.remote.caldav.RustNetworkException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * An abstract class that provides a standardized way to handle SDK calls and report failures.
 *
 * This class is intended to be extended by other classes that need to make SDK calls and handle exceptions in a consistent manner.
 * It provides methods to run suspend blocks with exception handling and to report flow failures without propagating them to the SDK consumer.
 *
 * @property crashReport An instance of [CrashReport] used for logging and reporting errors.
 */
public abstract class SdkCaller internal constructor(private val crashReport: CrashReport) {
    // private abstract val crashReport: CrashReport

    /**
     * Runs a suspend block and handles any exceptions that may occur during its execution.
     *
     * @param operation A string describing the operation being performed, used for logging and error reporting.
     * @param block The suspend block to be executed.
     * @return The result of the block if it completes successfully.
     * @throws CalendarSdkException If an exception occurs during the execution of the block, a [CalendarSdkException] is thrown with a descriptive message and the original exception as its cause.
     */
    internal suspend inline fun <T> runSdkCall(
        operation: String,
        crossinline block: suspend () -> T,
    ): T {
        val errorMessage = "Failed to $operation"
        return runCatching {
            block()
        }.cancellable().getOrElse { throwable ->
            throwable.logToSentryIfNeeded(errorMessage)
            throw CalendarSdkException(errorMessage, cause = throwable)
        }
    }

    /**
     * Extension function for [Flow] that catches any exceptions that occur during the flow's execution,
     * logs them appropriately, and prevents them from being propagated to the SDK consumer.
     *
     * @param operation A string describing the operation being performed, used for logging and error reporting.
     * @return A new [Flow] that will catch and log any exceptions that occur during its execution.
     */
    internal fun <T> Flow<T>.reportFlowFailures(operation: String): Flow<T> = catch { throwable ->
        // Flow errors are reported but intentionally not propagated to the SDK consumer.
        throwable.logToSentryIfNeeded(errorMessage = "Flow failed to $operation")
    }

    private fun Throwable.logToSentryIfNeeded(errorMessage: String) {
        when (this) {
            is CalendarSdkException -> throw this
            is RustNetworkException -> crashReport.addBreadcrumb(
                message = errorMessage,
                category = "network",
                level = CrashReportLevel.Warning,
            )
            else -> crashReport.capture(errorMessage, exception = this)
        }
    }
}
