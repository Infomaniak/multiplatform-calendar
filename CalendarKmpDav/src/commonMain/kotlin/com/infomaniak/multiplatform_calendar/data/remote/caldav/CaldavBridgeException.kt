/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.data.remote.caldav

import uniffi.caldav_bridge.CaldavException

/** Exception raised when the Rust bridge reports an error. */
open class CaldavBridgeException(
    override val message: String,
    override val cause: Throwable?,
) : RuntimeException(message, cause) {
    companion object {
        fun CaldavException.toCaldavBridgeException(methodName: String): CaldavBridgeException {
            val resolvedMessage = message ?: "Unknown error: $methodName"
            return when (this) {
                is CaldavException.RustNetworkException -> RustNetworkException(message = resolvedMessage, cause = this)
                is CaldavException.RustHttpException -> toCaldavHttpException(message = resolvedMessage)
                else -> CaldavBridgeException(message = resolvedMessage, cause = this)
            }
        }

        private fun CaldavException.RustHttpException.toCaldavHttpException(
            message: String,
        ): CaldavHttpException {
            return CaldavHttpException(statusCode = statusCode.toInt(), operation = operation, message = message, cause = this)
        }
    }
}

/** Dedicated exception raised when the Rust bridge reports network/connectivity failures. */
class RustNetworkException(
    override val message: String,
    override val cause: Throwable?,
) : CaldavBridgeException(message = message, cause = cause)

/**
 * Base exception for an HTTP status returned by the CalDAV server.
 *
 * @property statusCode Raw HTTP status code returned by the server.
 * @property operation DAV operation that produced the response.
 */
class CaldavHttpException(
    val statusCode: Int,
    val operation: String,
    override val message: String,
    override val cause: Throwable?,
) : CaldavBridgeException(
    message = message,
    cause = cause,
)
