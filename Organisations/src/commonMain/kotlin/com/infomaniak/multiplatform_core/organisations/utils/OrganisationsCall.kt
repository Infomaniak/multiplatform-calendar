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
package com.infomaniak.multiplatform_core.organisations.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsErrorCause
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsException

internal suspend inline fun <T> organisationsCall(crossinline block: suspend () -> T): T = withContext(Dispatchers.Default) {
    try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: OrganisationsException) {
        throw exception
    } catch (exception: IOException) {
        throw OrganisationsException(OrganisationsErrorCause.Network, exception)
    } catch (exception: Exception) {
        throw OrganisationsException(OrganisationsErrorCause.Unknown(exception), exception)
    }
}
