/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026-2026 Infomaniak Network SA
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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.Calendar
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSection
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarType
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.organisations.domain.model.Organisation
import com.infomaniak.multiplatform_core.organisations.domain.model.OrganisationId
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarsToSectionsTest {

    @Test
    fun calendarsAreGroupedByOrganisationSortedByName() {
        val first = calendar("a", organisationId = SECOND_ORGANISATION.id)
        val second = calendar("b", organisationId = FIRST_ORGANISATION.id)
        val third = calendar("c", organisationId = SECOND_ORGANISATION.id)

        val sections = listOf(first, second, third).toSections(
            organisationsByAccount = mapOf(ACCOUNT_ID to listOf(SECOND_ORGANISATION, FIRST_ORGANISATION)),
        )

        assertEquals(
            listOf(
                CalendarSection(ACCOUNT_ID, FIRST_ORGANISATION, listOf(second)),
                CalendarSection(ACCOUNT_ID, SECOND_ORGANISATION, listOf(first, third)),
            ),
            sections,
        )
    }

    @Test
    fun calendarsOfAnUnknownOrganisationComeLastWithoutOrganisation() {
        val withoutId = calendar("a", organisationId = null)
        val notSynced = calendar("b", organisationId = OrganisationId(99))
        val known = calendar("c", organisationId = FIRST_ORGANISATION.id)

        val sections = listOf(withoutId, notSynced, known).toSections(
            organisationsByAccount = mapOf(ACCOUNT_ID to listOf(FIRST_ORGANISATION)),
        )

        assertEquals(
            listOf(
                CalendarSection(ACCOUNT_ID, FIRST_ORGANISATION, listOf(known)),
                CalendarSection(ACCOUNT_ID, organisation = null, calendars = listOf(withoutId, notSynced)),
            ),
            sections,
        )
    }

    @Test
    fun sectionsFollowTheAccountsOrderAndAnOrganisationIsSplitPerAccount() {
        val ofOther = calendar("a", accountId = OTHER_ACCOUNT_ID, organisationId = FIRST_ORGANISATION.id)
        val ofAccount = calendar("b", organisationId = FIRST_ORGANISATION.id)

        val sections = listOf(ofOther, ofAccount).toSections(
            organisationsByAccount = mapOf(
                ACCOUNT_ID to listOf(FIRST_ORGANISATION),
                OTHER_ACCOUNT_ID to listOf(FIRST_ORGANISATION),
            ),
        )

        assertEquals(
            listOf(
                CalendarSection(ACCOUNT_ID, FIRST_ORGANISATION, listOf(ofAccount)),
                CalendarSection(OTHER_ACCOUNT_ID, FIRST_ORGANISATION, listOf(ofOther)),
            ),
            sections,
        )
    }

    @Test
    fun organisationsWithoutCalendarsHaveNoSection() {
        val calendar = calendar("a", organisationId = FIRST_ORGANISATION.id)

        val sections = listOf(calendar).toSections(
            organisationsByAccount = mapOf(
                ACCOUNT_ID to listOf(FIRST_ORGANISATION, SECOND_ORGANISATION),
                OTHER_ACCOUNT_ID to emptyList(),
            ),
        )

        assertEquals(listOf(CalendarSection(ACCOUNT_ID, FIRST_ORGANISATION, listOf(calendar))), sections)
    }

    @Test
    fun theOrganisationOfTheDefaultCalendarComesFirst() {
        val ofFirst = calendar("a", organisationId = FIRST_ORGANISATION.id)
        val default = calendar("b", organisationId = SECOND_ORGANISATION.id, isDefaultCalendar = true)

        val sections = listOf(ofFirst, default).toSections(
            organisationsByAccount = mapOf(ACCOUNT_ID to listOf(FIRST_ORGANISATION, SECOND_ORGANISATION)),
        )

        assertEquals(listOf(SECOND_ORGANISATION, FIRST_ORGANISATION), sections.map { it.organisation })
    }

    @Test
    fun theUnknownOrganisationStaysLastEvenWithTheDefaultCalendar() {
        val known = calendar("a", organisationId = FIRST_ORGANISATION.id)
        val default = calendar("b", organisationId = null, isDefaultCalendar = true)

        val sections = listOf(default, known).toSections(
            organisationsByAccount = mapOf(ACCOUNT_ID to listOf(FIRST_ORGANISATION)),
        )

        assertEquals(listOf(FIRST_ORGANISATION, null), sections.map { it.organisation })
    }

    @Test
    fun organisationsAreSortedIgnoringCase() {
        val lowercase = Organisation(OrganisationId(30), "alpha")
        val uppercase = Organisation(OrganisationId(40), "Beta")

        val sections = listOf(
            calendar("a", organisationId = uppercase.id),
            calendar("b", organisationId = lowercase.id),
        ).toSections(organisationsByAccount = mapOf(ACCOUNT_ID to listOf(uppercase, lowercase)))

        assertEquals(listOf(lowercase, uppercase), sections.map { it.organisation })
    }

    @Test
    fun calendarsComeDefaultFirstThenByNameIgnoringCaseThenRooms() {
        val room = calendar("Aaa room", organisationId = FIRST_ORGANISATION.id, calendarType = CalendarType.Bookable)
        val upper = calendar("Zeta", organisationId = FIRST_ORGANISATION.id)
        val lower = calendar("beta", organisationId = FIRST_ORGANISATION.id)
        val default = calendar("Yota", organisationId = FIRST_ORGANISATION.id, isDefaultCalendar = true)

        val sections = listOf(room, upper, lower, default).toSections(
            organisationsByAccount = mapOf(ACCOUNT_ID to listOf(FIRST_ORGANISATION)),
        )

        assertEquals(listOf(default, lower, upper, room), sections.single().calendars)
    }

    private fun calendar(
        name: String,
        accountId: AccountId = ACCOUNT_ID,
        organisationId: OrganisationId?,
        isDefaultCalendar: Boolean = false,
        calendarType: CalendarType = CalendarType.Basic,
    ) = Calendar(
        id = CalendarId("https://dav.example/cal/$name/"),
        accountId = accountId,
        displayName = name,
        colors = CalendarColors.from(null),
        isVisible = true,
        organisationId = organisationId,
        calendarType = calendarType,
        isDefaultCalendar = isDefaultCalendar,
    )

    private companion object {
        val ACCOUNT_ID = AccountId(1)
        val OTHER_ACCOUNT_ID = AccountId(2)
        val FIRST_ORGANISATION = Organisation(OrganisationId(10), "Alpha")
        val SECOND_ORGANISATION = Organisation(OrganisationId(20), "Beta")
    }
}
