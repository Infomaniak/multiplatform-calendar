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
package com.infomaniak.multiplatform_core.organisations.data.repository

import com.infomaniak.multiplatform_core.organisations.data.local.OrganisationsDatabase
import com.infomaniak.multiplatform_core.organisations.data.local.entity.OrganisationEntity
import com.infomaniak.multiplatform_core.organisations.data.remote.OrganisationsRemoteDataSource
import com.infomaniak.multiplatform_core.organisations.data.remote.model.ApiOrganisation
import com.infomaniak.multiplatform_core.organisations.domain.model.Organisation
import com.infomaniak.multiplatform_core.organisations.domain.model.OrganisationId
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsErrorCause
import com.infomaniak.multiplatform_core.organisations.domain.model.exceptions.OrganisationsException
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.account.utils.InMemoryAccountTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

internal class OrganisationsRepository(
    database: OrganisationsDatabase,
    private val remoteDataSource: OrganisationsRemoteDataSource,
    private val tokenByAccount: InMemoryAccountTokens,
) {

    private val dao = database.organisationDao()
    private val syncMutexes = mutableMapOf<AccountId, Mutex>()
    private val syncMutexesLock = Mutex()

    suspend fun initAccount(accountId: AccountId, accessToken: AccessToken) = tokenByAccount.put(accountId, accessToken)

    /** Holds the sync mutex, so that a running sync cannot write the account back. */
    suspend fun removeAccount(accountId: AccountId) {
        syncMutex(accountId).withLock {
            tokenByAccount.remove(accountId)
            dao.deleteOrganisationsForAccount(accountId)
        }
    }

    suspend fun sync(accountIds: Set<AccountId>) {
        val failures = coroutineScope {
            accountIds.ifEmpty { tokenByAccount.getAccountIds() }
                .map { accountId -> async { catchFailure { syncOrganisationsOf(accountId) } } }
                .awaitAll()
                .filterNotNull()
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    suspend fun getOrganisation(accountId: AccountId, id: OrganisationId): Organisation? =
        dao.getOrganisation(accountId, id)?.toOrganisation()

    fun observeOrganisations(accountId: AccountId): Flow<List<Organisation>> =
        dao.observeOrganisations(accountId).map { entities -> entities.map { it.toOrganisation() } }

    private suspend fun catchFailure(block: suspend () -> Unit): Exception? = try {
        block()
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        exception
    }

    private suspend fun syncOrganisationsOf(accountId: AccountId) {
        syncMutex(accountId).withLock {
            val organisations = remoteDataSource.getOrganisations(getToken(accountId)).map { it.toEntity(accountId) }
            dao.replaceOrganisations(accountId, organisations)
        }
    }

    private suspend fun getToken(accountId: AccountId): AccessToken =
        tokenByAccount.getToken(accountId) ?: throw OrganisationsException(OrganisationsErrorCause.AccountNotInitialized(accountId))

    private suspend fun syncMutex(accountId: AccountId): Mutex = syncMutexesLock.withLock {
        syncMutexes.getOrPut(accountId) { Mutex() }
    }
}

private fun ApiOrganisation.toEntity(accountId: AccountId): OrganisationEntity =
    OrganisationEntity(accountId = accountId, id = OrganisationId(id), name = name)

private fun OrganisationEntity.toOrganisation(): Organisation = Organisation(id = id, name = name)
