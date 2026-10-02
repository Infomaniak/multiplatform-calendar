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
package com.infomaniak.multiplatform_core.contacts

import com.infomaniak.multiplatform_core.account.domain.model.AccessToken
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.contacts.data.local.ContactsDatabase
import com.infomaniak.multiplatform_core.contacts.data.local.ContactsDatabaseSource
import com.infomaniak.multiplatform_core.contacts.data.local.contactsDatabase
import com.infomaniak.multiplatform_core.contacts.data.remote.ContactsRemoteDataSource
import com.infomaniak.multiplatform_core.contacts.data.remote.createContactsHttpClient
import com.infomaniak.multiplatform_core.contacts.data.repository.ContactsRepository
import com.infomaniak.multiplatform_core.contacts.domain.model.Contact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContactsProvider
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsErrorCause
import com.infomaniak.multiplatform_core.contacts.domain.model.exceptions.ContactsException
import com.infomaniak.multiplatform_core.contacts.utils.TokenStore
import com.infomaniak.multiplatform_core.contacts.utils.contactsCall
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Entry point of the contacts module. */
public class ContactsManager internal constructor(
    internal val database: ContactsDatabase,
    httpClient: HttpClient,
    deviceContactsProvider: DeviceContactsProvider,
    deviceContactsScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    internal val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val repository = ContactsRepository(
        database = database,
        remoteDataSource = ContactsRemoteDataSource(httpClient),
        deviceContactsProvider = deviceContactsProvider,
        tokenStore = TokenStore(),
        deviceContactsScope = deviceContactsScope,
    )

    /**
     * @param databasePath Absolute path of the contacts database file.
     * @param deviceContactsProvider Platform-specific reader of the device contacts.
     */
    public constructor(
        databasePath: String,
        deviceContactsProvider: DeviceContactsProvider,
    ) : this(
        database = contactsDatabase(ContactsDatabaseSource.File(databasePath)),
        deviceContactsProvider = deviceContactsProvider,
        httpClient = createContactsHttpClient(),
    )

    /**
     * Registers [accountId] with its [accessToken], kept in memory only, then syncs its address book in the background.
     * Call it at startup and on login. A failed background sync is ignored; the next one retries.
     */
    @Throws(ContactsException::class, CancellationException::class)
    public suspend fun initAccount(accountId: AccountId, accessToken: AccessToken): Unit = contactsCall {
        repository.initAccount(accountId, accessToken)
        syncScope.launch {
            try {
                repository.sync(setOf(accountId))
            } catch (_: Exception) {
                // Local data stays usable; the next sync retries.
            }
        }
    }

    /** Forgets the token of [accountId] and deletes its local contacts. */
    @Throws(ContactsException::class, CancellationException::class)
    public suspend fun removeAccount(accountId: AccountId): Unit = contactsCall { repository.removeAccount(accountId) }

    /**
     * Returns the contacts matching [query], merging the locally synced server contacts with the device contacts,
     * sorted by times contacted, then match quality (start of text, of a word, inside), then server before device,
     * then name. Contacts without a name or not in an address book come last. Never hits the network.
     *
     * @param accountIds Accounts to search, or every synced account when empty. A contact found in several of them is
     * returned once, with its contacted times added up.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(ContactsException::class, CancellationException::class)
    public suspend fun search(
        query: String,
        accountIds: Set<AccountId> = emptySet(),
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): List<Contact> = contactsCall { repository.search(accountIds, query, limit) }

    /**
     * Syncs the server address books into the local database, in parallel. When some accounts fail, the others still
     * complete, then the first failure is thrown.
     *
     * @param accountIds Accounts to sync, or every initialized account when empty.
     * @throws ContactsException [ContactsErrorCause.AccountNotInitialized] for a requested account without a token.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Throws(ContactsException::class, CancellationException::class)
    public suspend fun sync(accountIds: Set<AccountId> = emptySet()): Unit = contactsCall { repository.sync(accountIds) }

    internal companion object {
        const val DEFAULT_SEARCH_LIMIT: Int = 10
    }
}
