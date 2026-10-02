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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Contacts.CNLabelHome
import platform.Contacts.CNLabelWork
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNMutableContact
import platform.Foundation.NSString
import platform.Foundation.create
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
class AppleDeviceContactsProviderTest {

    @Test
    fun emailsAreReadFromLabeledValues() {
        val contact = CNMutableContact().apply {
            setEmailAddresses(
                listOf(
                    CNLabeledValue.labeledValueWithLabel(CNLabelHome, NSString.create(string = "john@x.com")),
                    CNLabeledValue.labeledValueWithLabel(CNLabelWork, NSString.create(string = " ")),
                ),
            )
        }

        assertEquals(listOf("john@x.com"), contact.emails())
    }
}
