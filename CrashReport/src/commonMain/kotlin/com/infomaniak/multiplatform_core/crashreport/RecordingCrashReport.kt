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
package com.infomaniak.multiplatform_core.crashreport

public class RecordingCrashReport : CrashReport {

    public data class Capture(val message: String, val data: Map<String, String>?, val level: CrashReportLevel?)
    public data class ExceptionCapture(val message: String, val exception: Throwable)

    private val _captures = mutableListOf<Capture>()
    public val captures: List<Capture> get() = _captures

    private val _exceptionCaptures = mutableListOf<ExceptionCapture>()
    public val exceptionCaptures: List<ExceptionCapture> get() = _exceptionCaptures

    override fun addBreadcrumb(
        message: String,
        category: String,
        level: CrashReportLevel,
        type: BreadcrumbType,
        data: Map<String, String>?,
    ): Unit = Unit

    override fun capture(message: String, exception: Throwable, data: Map<String, String>?) {
        _exceptionCaptures += ExceptionCapture(message, exception)
    }

    override fun capture(message: String, data: Map<String, String>?, level: CrashReportLevel?) {
        _captures += Capture(message, data, level)
    }
}
