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
import com.infomaniak.multiplatform_core.contacts.domain.model.ContactAvatar
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContactsProvider
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import com.infomaniak.multiplatform_core.contacts.utils.MatchTier
import com.infomaniak.multiplatform_core.contacts.utils.TokenStore
import com.infomaniak.multiplatform_core.contacts.utils.contactNameComparator
import com.infomaniak.multiplatform_core.contacts.utils.normalizedForSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

internal class ContactsRepository(
    private val database: ContactsDatabase,
    private val remoteDataSource: ContactsRemoteDataSource,
    private val deviceContactsProvider: DeviceContactsProvider,
    private val tokenStore: TokenStore,
    private val deviceContactsScope: CoroutineScope,
) {

    private val dao = database.contactDao()
    private val syncMutexes = mutableMapOf<AccountId, Mutex>()
    private val syncMutexesLock = Mutex()
    private val deviceContactsMutex = Mutex()
    private var deviceContacts: List<DeviceContact>? = null
    private var isObservingDeviceContacts = false
    private val deviceContactsVersion = MutableStateFlow(0)

    /** Searches [accountIds], or every synced account when empty. A contact found in several accounts is returned once. */
    suspend fun search(accountIds: Set<AccountId>, query: String, limit: Int): List<Contact> {
        val normalizedQuery = query.normalizedForSearch()
        if (normalizedQuery.isEmpty()) return emptyList()

        val likeQuery = "%${normalizedQuery.escapeLikeWildcards()}%"
        val apiMatches = dao.search(accountIds.ifEmpty { dao.accountIds().toSet() }, likeQuery)
            .map(ContactEntity::toContactMatch)
            .mergedAcrossAccounts()

        val deviceMatches = cachedDeviceContacts()
            .filter { it.matches(normalizedQuery) }
            .map(DeviceContact::toContactMatch)

        val matches = merge(deviceMatches, apiMatches)
        val tiers = matches.associateWith {
            MatchTier.of(normalizedQuery, it.name.normalizedForSearch(), it.email.normalizedForSearch())
        }
        return matches
            .sortedWith(contactMatchComparator(tiers::getValue))
            .take(limit)
            .map(ContactMatch::toContact)
    }

    fun observeContacts(emails: Set<String>, preferredAccountId: AccountId?): Flow<Map<String, Contact>> {
        if (emails.isEmpty()) return flowOf(emptyMap())

        return combine(
            observeApiMatches(emails, preferredAccountId),
            observeDeviceMatches(emails),
        ) { apiMatches, deviceMatches ->
            bestContactsByEmail(emails, merge(deviceMatches, apiMatches))
        }.distinctUntilChanged()
    }

    fun onDeviceContactsAccessGranted() = deviceContactsVersion.update { it + 1 }

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

    private fun observeApiMatches(emails: Set<String>, preferredAccountId: AccountId?): Flow<List<ContactMatch>> {
        return dao.observeByEmails(emails.mapTo(mutableSetOf()) { it.normalizedForSearch() }).map { entities ->
            entities
                .sortedByDescending { it.accountId == preferredAccountId }
                .map(ContactEntity::toContactMatch)
                .mergedAcrossAccounts()
        }
    }

    private fun observeDeviceMatches(emails: Set<String>): Flow<List<ContactMatch>> {
        val lowercaseEmails = emails.mapTo(mutableSetOf()) { it.lowercase() }
        return deviceContactsVersion.map {
            cachedDeviceContacts()
                .filter { it.email.lowercase() in lowercaseEmails }
                .map(DeviceContact::toContactMatch)
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

    /**
     * Device contacts are read once then cached until the system reports a change. A read denied for lack of permission is
     * not cached, so contacts show up as soon as the app gets the permission.
     */
    private suspend fun cachedDeviceContacts(): List<DeviceContact> = deviceContactsMutex.withLock {
        deviceContacts ?: deviceContactsProvider.read()?.also {
            deviceContacts = it
            observeDeviceContactsChanges()
        }.orEmpty()
    }

    /** Starts after a granted read, as observing the contacts requires the permission on Android. */
    private fun observeDeviceContactsChanges() {
        if (isObservingDeviceContacts) return
        isObservingDeviceContacts = true
        deviceContactsScope.launch {
            deviceContactsProvider.changes.collect {
                deviceContactsMutex.withLock { deviceContacts = null }
                deviceContactsVersion.update { it + 1 }
            }
        }
    }
}

private fun merge(deviceContacts: List<ContactMatch>, apiContacts: List<ContactMatch>): List<ContactMatch> {
    // Device contacts sharing a key keep the one with a photo, whatever their order.
    val mergedByKey = deviceContacts.groupBy(ContactMatch::key)
        .mapValuesTo(mutableMapOf()) { (_, matches) -> matches.firstOrNull { it.avatar != null } ?: matches.first() }
    apiContacts.forEach { apiContact ->
        mergedByKey[apiContact.key] = mergedByKey[apiContact.key]?.completedWith(apiContact) ?: apiContact
    }
    return mergedByKey.values.toList()
}

/** The device contact, completed with what only the API knows: its avatar if it has none, and its ranking. */
private fun ContactMatch.completedWith(apiContact: ContactMatch): ContactMatch = copy(
    avatar = avatar ?: apiContact.avatar,
    comesFromApi = true,
    contactedTimes = apiContact.contactedTimes,
    isInAddressBook = apiContact.isInAddressBook,
)

private fun bestContactsByEmail(emails: Set<String>, matches: List<ContactMatch>): Map<String, Contact> {
    if (matches.isEmpty()) return emptyMap()

    val matchesByEmail = matches.groupBy { it.email.lowercase() }
    return emails.mapNotNull { email ->
        matchesByEmail[email.lowercase()]?.let { email to it.bestRanked().toContact() }
    }.toMap()
}

private fun List<ContactMatch>.bestRanked(): ContactMatch = minWith(contactMatchComparator { MatchTier.None })

private fun contactMatchComparator(matchTier: (ContactMatch) -> MatchTier): Comparator<ContactMatch> =
    compareBy<ContactMatch> { -relevanceWeight(it) }
        .thenBy(matchTier)
        .thenBy { if (it.comesFromApi) 0 else 1 }
        .thenComparator { left, right -> contactNameComparator.compare(left.name, right.name) }

/** Relevance weight: times contacted, or -1 when the contact is not a real one (no name, or not in an address book). */
private fun relevanceWeight(contact: ContactMatch): Int =
    if (contact.name.isBlank() || !contact.isInAddressBook) -1 else contact.contactedTimes ?: 0

/**
 * Contacted times are added up, and the contact is in an address book when any account has it in one. The avatar is
 * the first one found, with the account it comes from.
 */
private fun List<ContactMatch>.mergedAcrossAccounts(): List<ContactMatch> =
    groupBy(ContactMatch::key).values.map { it.reduce(ContactMatch::mergedWith) }

private fun ContactMatch.mergedWith(contact: ContactMatch): ContactMatch = copy(
    avatar = avatar ?: contact.avatar,
    contactedTimes = contactedTimesSum(contactedTimes, contact.contactedTimes),
    isInAddressBook = isInAddressBook || contact.isInAddressBook,
)

/** Null when neither count is known. */
private fun contactedTimesSum(first: Int?, second: Int?): Int? =
    if (first == null && second == null) null else (first ?: 0) + (second ?: 0)

private fun ContactEntity.toContactMatch(): ContactMatch = ContactMatch(
    email = email,
    name = name,
    avatar = avatarUrl?.let { ContactAvatar.Remote(url = it, accountId = accountId) },
    comesFromApi = true,
    contactedTimes = contactedTimes,
    isInAddressBook = isInAddressBook,
)

private fun DeviceContact.matches(normalizedQuery: String): Boolean =
    email.isNotBlank() && (name.normalizedForSearch().contains(normalizedQuery) || email.normalizedForSearch().contains(normalizedQuery))

private fun DeviceContact.toContactMatch(): ContactMatch = ContactMatch(
    email = email,
    name = name,
    avatar = avatarId?.let(ContactAvatar::Device),
    comesFromApi = false,
    contactedTimes = null,
    isInAddressBook = true,
)

private fun ApiContact.toEntities(accountId: AccountId): List<ContactEntity> {
    val contactName = name.orEmpty()
    return emails.filter { it.isNotBlank() }.map { email ->
        ContactEntity(
            accountId = accountId,
            email = email,
            name = contactName,
            avatarUrl = avatar?.let { "https://$MAIL_API_HOST$it" },
            contactedTimes = contactedTimes?.get(email),
            isInAddressBook = !other,
            nameNormalized = contactName.normalizedForSearch(),
            emailNormalized = email.normalizedForSearch(),
        )
    }
}

private fun String.escapeLikeWildcards(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
