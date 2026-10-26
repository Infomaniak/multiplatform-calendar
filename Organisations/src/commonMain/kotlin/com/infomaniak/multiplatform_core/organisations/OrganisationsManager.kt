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
package com.infomaniak.multiplatform_core.organisations

import com.infomaniak.multiplatform_core.organisations.data.local.OrganisationsDatabase
import com.infomaniak.multiplatform_core.organisations.data.remote.OrganisationsRemoteDataSource
import com.infomaniak.multiplatform_core.organisations.data.remote.createOrganisationsHttpClient
import com.infomaniak.multiplatform_core.organisations.data.repository.OrganisationsRepository
import com.infomaniak.multiplatform_core.organisations.domain.model.Organisation
import com.infomaniak.multiplatform_core.organisations.domain.model.OrganisationId
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsException
import com.infomaniak.multiplatform_core.organisations.utils.organisationsCall
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.account.utils.InMemoryAccountTokens
import io.ktor.client.HttpClient
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

public class OrganisationsManager internal constructor(
    internal val database: OrganisationsDatabase,
    httpClient: HttpClient,
) {

    private val repository = OrganisationsRepository(
        database = database,
        remoteDataSource = OrganisationsRemoteDataSource(httpClient),
        tokenByAccount = InMemoryAccountTokens(),
    )

    public constructor(database: OrganisationsDatabase) : this(
        database = database,
        httpClient = createOrganisationsHttpClient(),
    )

    /** The token is kept in memory only: call it at startup and on login. Does not sync. */
    @Throws(OrganisationsException::class, CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, accessToken: AccessToken): Unit =
        organisationsCall { repository.initAccount(accountId, accessToken) }

    /** Also deletes the local organisations of [accountId]. */
    @Throws(OrganisationsException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = organisationsCall { repository.removeAccount(accountId) }

    /**
     * When some accounts fail, the others still complete, then the first failure is thrown.
     * @param accountIds Every initialized account when empty.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(OrganisationsException::class, CancellationException::class)
    public suspend fun sync(accountIds: Set<AccountId> = emptySet()): Unit = organisationsCall { repository.sync(accountIds) }

    /** Reads the local organisations only. */
    @Throws(OrganisationsException::class, CancellationException::class)
    public suspend fun getOrganisation(accountId: AccountId, id: OrganisationId): Organisation? =
        organisationsCall { repository.getOrganisation(accountId, id) }
}
