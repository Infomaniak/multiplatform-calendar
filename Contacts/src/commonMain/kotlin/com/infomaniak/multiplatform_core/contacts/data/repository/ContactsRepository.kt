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
package com.infomaniak.multiplatform_core.contacts.data.repository

import androidx.room3.withWriteTransaction
import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.data.local.ContactsDatabase
import com.infomaniak.multiplatform_core.contacts.data.local.entity.ContactAccountEntity
import com.infomaniak.multiplatform_core.contacts.data.local.entity.ContactEntity
import com.infomaniak.multiplatform_core.contacts.data.remote.ContactsFetch
import com.infomaniak.multiplatform_core.contacts.data.remote.ContactsRemoteDataSource
import com.infomaniak.multiplatform_core.contacts.data.remote.MAIL_API_HOST
import com.infomaniak.multiplatform_core.contacts.data.remote.model.ApiContact
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import com.infomaniak.multiplatform_core.contacts.utils.TokenStore
import com.infomaniak.multiplatform_core.contacts.utils.contactNameComparator
import com.infomaniak.multiplatform_core.contacts.utils.normalizedForSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

internal class ContactsRepository(
    private val database: ContactsDatabase,
    private val remoteDataSource: ContactsRemoteDataSource,
    private val tokenStore: TokenStore,
) {

    private val dao = database.contactDao()
    private val syncMutexes = mutableMapOf<AccountId, Mutex>()
    private val syncMutexesLock = Mutex()

    /** Searches [accountIds], or every synced account when empty. A contact found in several accounts is returned once. */
    suspend fun search(accountIds: Set<AccountId>, query: String, limit: Int): List<Contact> {
        val normalizedQuery = query.normalizedForSearch()
        if (normalizedQuery.isEmpty()) return emptyList()

        val likeQuery = "%${normalizedQuery.escapeLikeWildcards()}%"
        return dao.search(accountIds.ifEmpty { dao.accountIds().toSet() }, likeQuery)
            .mergedAcrossAccounts()
            .sortedWith(contactComparator())
            .take(limit)
            .map(ContactEntity::toContact)
    }

    /**
     * Syncs [accountIds], or every initialized account when empty, in parallel. When some accounts fail, the others
     * still complete, then the first failure is thrown with the next ones suppressed.
     */
    suspend fun sync(accountIds: Set<AccountId>) {
        val failures = coroutineScope {
            accountIds.ifEmpty { tokenStore.accountIds() }
                .map { accountId -> async { syncFailure(accountId) } }
                .awaitAll()
                .filterNotNull()
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    suspend fun initAccount(accountId: AccountId, accessToken: AccessToken) = tokenStore.put(accountId, accessToken)

    /** Takes the sync mutex of [accountId] so that a running sync cannot write the account back after its removal. */
    suspend fun removeAccount(accountId: AccountId) {
        syncMutex(accountId).withLock {
            tokenStore.remove(accountId)
            dao.deleteAccount(accountId)
        }
    }

    private suspend fun syncFailure(accountId: AccountId): Exception? = try {
        syncAccount(accountId)
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        exception
    }

    private suspend fun syncAccount(accountId: AccountId) {
        syncMutex(accountId).withLock {
            val token = tokenStore.get(accountId) ?: throw ContactsException(ContactsErrorCause.AccountNotInitialized(accountId))
            when (val fetch = remoteDataSource.getContacts(token, dao.account(accountId)?.etag)) {
                is ContactsFetch.NotModified -> Unit
                is ContactsFetch.Success -> database.withWriteTransaction {
                    dao.upsertAccount(ContactAccountEntity(id = accountId, etag = fetch.etag))
                    dao.deleteContactsForAccount(accountId)
                    dao.upsertContacts(fetch.contacts.flatMap { it.toEntities(accountId) })
                }
            }
        }
    }

    private suspend fun syncMutex(accountId: AccountId): Mutex = syncMutexesLock.withLock {
        syncMutexes.getOrPut(accountId) { Mutex() }
    }
}

private fun contactComparator(): Comparator<ContactEntity> =
    compareBy<ContactEntity> { -relevanceWeight(it) }
        .thenComparator { left, right -> contactNameComparator.compare(left.name, right.name) }

/** Relevance weight: times contacted, or -1 when the contact is not a real one (no name, or `other`). */
private fun relevanceWeight(contact: ContactEntity): Int =
    if (contact.name.isBlank() || contact.other) -1 else contact.contactedTimes ?: 0

/** Contacted times are added up, and the contact is `other` only if it is in every account. */
private fun List<ContactEntity>.mergedAcrossAccounts(): List<ContactEntity> =
    groupBy { it.email.lowercase() to it.name }.values.map { it.reduce(ContactEntity::mergedWith) }

private fun ContactEntity.mergedWith(contact: ContactEntity): ContactEntity = copy(
    avatarUrl = avatarUrl ?: contact.avatarUrl,
    contactedTimes = if (contactedTimes == null && contact.contactedTimes == null) {
        null
    } else {
        (contactedTimes ?: 0) + (contact.contactedTimes ?: 0)
    },
    other = other && contact.other,
)

private fun ContactEntity.toContact(): Contact = Contact(email = email, name = name, avatarUrl = avatarUrl)

private fun ApiContact.toEntities(accountId: AccountId): List<ContactEntity> {
    val contactName = name.orEmpty()
    return emails.filter { it.isNotBlank() }.map { email ->
        ContactEntity(
            accountId = accountId,
            email = email,
            name = contactName,
            avatarUrl = avatar?.let { "https://$MAIL_API_HOST$it" },
            contactedTimes = contactedTimes?.get(email),
            other = other,
            nameNormalized = contactName.normalizedForSearch(),
            emailNormalized = email.normalizedForSearch(),
        )
    }
}

private fun String.escapeLikeWildcards(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
