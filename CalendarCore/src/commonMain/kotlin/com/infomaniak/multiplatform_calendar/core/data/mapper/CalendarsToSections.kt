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
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarSection
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarType
import com.infomaniak.multiplatform_core.account.domain.model.AccountId
import com.infomaniak.multiplatform_core.organisations.domain.model.Organisation

/**
 * Per account, in the order of [organisationsByAccount]: the organisation holding the default calendar, the other ones
 * by name, then the unknown organisation. Within a section: the default calendar, the others by name, then the rooms.
 */
internal fun List<Calendar>.toSections(organisationsByAccount: Map<AccountId, List<Organisation>>): List<CalendarSection> {
    val calendarsByAccount = groupBy(Calendar::accountId)
    return organisationsByAccount.flatMap { (accountId, organisations) ->
        val organisationsById = organisations.associateBy(Organisation::id)
        calendarsByAccount[accountId].orEmpty()
            .groupBy { calendar -> calendar.organisationId?.let(organisationsById::get) }
            .map { (organisation, calendars) -> CalendarSection(accountId, organisation, calendars.sortedWith(calendarOrder)) }
            .sortedWith(sectionOrder)
    }
}

private val sectionOrder = compareBy<CalendarSection>(
    { it.organisation == null },
    { section -> section.calendars.none(Calendar::isDefaultCalendar) },
).thenBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.organisation?.name }

private val calendarOrder = compareBy<Calendar>(
    { !it.isDefaultCalendar },
    { it.calendarType == CalendarType.Bookable },
).thenBy(String.CASE_INSENSITIVE_ORDER, Calendar::displayName)
