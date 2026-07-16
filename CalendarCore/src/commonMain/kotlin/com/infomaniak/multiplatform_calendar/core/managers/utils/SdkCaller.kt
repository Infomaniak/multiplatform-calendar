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
package com.infomaniak.multiplatform_calendar.core.managers.utils

import com.infomaniak.multiplatform_calendar.core.crashreporting.CrashReport
import com.infomaniak.multiplatform_calendar.core.crashreporting.CrashReportLevel
import com.infomaniak.multiplatform_calendar.core.domain.model.exceptions.CalendarSdkException
import com.infomaniak.multiplatform_calendar.core.forCoreKmp.cancellable
import com.infomaniak.multiplatform_calendar.data.remote.caldav.RustNetworkException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Provides consistent error handling for SDK operations.
 *
 * Suspend operations wrap failures in [CalendarSdkException], while flow failures
 * are reported and suppressed unless they are already [CalendarSdkException].
 *
 * @property crashReport An instance of [CrashReport] used for logging and reporting errors.
 */
@Inject
@SingleIn(AppScope::class)
internal class SdkCaller(private val crashReport: CrashReport) {

    /**
     * Executes [block] and converts failures to [CalendarSdkException].
     *
     * Cancellation and existing [CalendarSdkException] instances are propagated unchanged.
     *
     * @param operation Description used in error reports.
     * @throws CalendarSdkException If the operation fails.
     */
    internal suspend inline fun <T> run(
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
     * Returns a flow that reports and suppresses upstream failures.
     *
     * Existing [CalendarSdkException] instances are propagated unchanged.
     *
     * @param operation Description used in error reports.
     */
    internal inline fun <T> flow(operation: String, block: () -> Flow<T>) = block().reportFlowFailures(operation)

    private fun <T> Flow<T>.reportFlowFailures(operation: String): Flow<T> = catch { throwable ->
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
