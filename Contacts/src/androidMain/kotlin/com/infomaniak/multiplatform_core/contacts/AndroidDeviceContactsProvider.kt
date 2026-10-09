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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.util.Patterns
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContact
import com.infomaniak.multiplatform_core.contacts.domain.model.DeviceContactsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/** Reads the device contacts through [ContactsContract]. */
public class AndroidDeviceContactsProvider(private val appContext: Context) : DeviceContactsProvider {

    override val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        appContext.contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
        trySend(Unit)
        awaitClose { appContext.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    override suspend fun read(): List<DeviceContact>? = withContext(Dispatchers.IO) {
        if (appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }

        val deviceContacts = mutableListOf<DeviceContact>()
        appContext.contentResolver.query(
            Email.CONTENT_URI,
            arrayOf(Email.ADDRESS, Email.DISPLAY_NAME_PRIMARY, Email.PHOTO_THUMBNAIL_URI),
            null,
            null,
            null,
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Email.ADDRESS)
            val nameIndex = cursor.getColumnIndexOrThrow(Email.DISPLAY_NAME_PRIMARY)
            val photoIndex = cursor.getColumnIndexOrThrow(Email.PHOTO_THUMBNAIL_URI)
            while (cursor.moveToNext()) {
                val email = cursor.getString(addressIndex)?.takeIf { it.isEmail() } ?: continue
                deviceContacts.add(
                    DeviceContact(
                        email = email,
                        name = cursor.getString(nameIndex).orEmpty(),
                        avatarId = cursor.getString(photoIndex),
                    ),
                )
            }
        }

        deviceContacts.distinct()
    }

    private fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()
}
