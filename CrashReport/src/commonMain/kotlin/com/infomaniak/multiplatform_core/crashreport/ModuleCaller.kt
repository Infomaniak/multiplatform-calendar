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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Runs a module's public calls off the main thread, reports their failures and converts them into the module's [E]. */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public abstract class ModuleCaller<E>(
    private val crashReport: CrashReport,
    private val errorMessage: String,
) where E : Exception, E : ModuleHandledException {

    public suspend inline operator fun <T> invoke(crossinline block: suspend () -> T): T = withContext(Dispatchers.Default) {
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw reportAndConvert(exception)
        }
    }

    protected abstract fun kindOf(failure: Throwable): FailureKind

    protected abstract fun convert(failure: Exception): E

    @PublishedApi
    internal fun reportAndConvert(failure: Exception): E {
        crashReport.reportFailure(failure, errorMessage, ::kindOf)
        return convert(failure)
    }
}
