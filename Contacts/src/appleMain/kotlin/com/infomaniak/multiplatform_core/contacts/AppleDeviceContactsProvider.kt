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

import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContactsProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusLimited
import platform.Contacts.CNContact
import platform.Contacts.CNContactEmailAddressesKey
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactFormatter
import platform.Contacts.CNContactFormatterStyle
import platform.Contacts.CNContactStore
import platform.Contacts.CNContactStoreDidChangeNotification
import platform.Contacts.CNEntityType
import platform.Contacts.CNLabeledValue
import platform.Foundation.NSNotificationCenter

/** Reads the device contacts through the Contacts framework. */
@OptIn(ExperimentalForeignApi::class)
public class AppleDeviceContactsProvider : DeviceContactsProvider {

    override val changes: Flow<Unit> = callbackFlow {
        val notificationCenter = NSNotificationCenter.defaultCenter
        val observer = notificationCenter.addObserverForName(CNContactStoreDidChangeNotification, null, null) { _ ->
            trySend(Unit)
        }
        trySend(Unit)
        awaitClose { notificationCenter.removeObserver(observer) }
    }.conflate()

    override suspend fun read(): List<DeviceContact>? = withContext(Dispatchers.IO) {
        if (!isAccessGranted()) return@withContext null

        val request = CNContactFetchRequest(
            keysToFetch = listOf(
                CNContactEmailAddressesKey,
                CNContactFormatter.descriptorForRequiredKeysForStyle(CNContactFormatterStyle.CNContactFormatterStyleFullName),
            ),
        )

        val contacts = mutableListOf<DeviceContact>()
        val succeeded = CNContactStore().enumerateContactsWithFetchRequest(fetchRequest = request, error = null) { contact, _ ->
            contact?.let { nonNullContact ->
                val name = CNContactFormatter.stringFromContact(nonNullContact, CNContactFormatterStyle.CNContactFormatterStyleFullName).orEmpty()
                nonNullContact.emails().forEach { email -> contacts.add(DeviceContact(email = email, name = name)) }
            }
        }

        if (succeeded) contacts.distinct() else null
    }

    /** The app owns the permission request; until access is granted, no device contact is read. */
    private fun isAccessGranted(): Boolean =
        when (CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)) {
            CNAuthorizationStatusAuthorized, CNAuthorizationStatusLimited -> true
            else -> false
        }
}

internal fun CNContact.emails(): List<String> =
    emailAddresses.mapNotNull { (it as? CNLabeledValue)?.value()?.toString() }.filter { it.isNotBlank() }
