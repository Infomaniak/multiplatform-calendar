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
package com.infomaniak.multiplatform_calendar.core.data.mapper

import com.infomaniak.multiplatform_calendar.core.data.exception.CaldavParsingException
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseCss3ColorName
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseHexColor
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseICalDateTime
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventRef

@Throws(CaldavParsingException::class)
internal fun RemoteDavEvent.toEntity(calendarId: CalendarId): EventEntity {
    return EventEntity(
        id = EventId(url),
        calendarId = calendarId,
        summary = summary ?: "",
        description = description,
        location = location,
        timing = toTimingEntity(),
        created = parseICalDateTime(created),
        lastModified = parseICalDateTime(lastModified),
        dtStamp = parseICalDateTime(dtstamp),
        rrule = rrule,
        status = EventStatus.fromIcalString(status),
        transp = transp,
        classification = Classification.fromIcalString(classification),
        priority = priority?.toIntOrNull(),
        sequence = sequence?.toIntOrNull(),
        categories = parseICalCategories(categories),
        attendees = attendees.map { it.toEntity() },
        alarms = alarms.map { it.toEntity() },
        etag = etag,
        colorArgb = resolveColorArgb(),
        colorIcalName = colorIcalName,
    )
}

/** Resolve the wire's color into a single ARGB: Apple hex wins over the RFC 7986 CSS3 name. */
private fun RemoteDavEvent.resolveColorArgb(): Int? {
    return parseHexColor(colorHex) ?: parseCss3ColorName(colorIcalName)
}

/**
 * Persist a freshly built/patched event (from [CalendarSyncRemoteSource.buildEventIcs] /
 * [CalendarSyncRemoteSource.patchEventIcs]) as a local row, binding it to the server-assigned
 * [ref] (href + etag) and marking it synced. All parsed fields come straight from the ICS, so the
 * row mirrors exactly what was written to the server.
 */
@Throws(CaldavParsingException::class)
internal fun RemoteDavEvent.toSyncedEntity(ref: RemoteDavEventRef, calendarId: CalendarId): EventEntity {
    return copy(url = ref.url, etag = ref.etag).toEntity(calendarId).copy(isSynced = true)
}


private val CATEGORY_TOKEN = Regex("""(?:\\.|[^,])+""")
private val TEXT_ESCAPE = Regex("""\\[\\;,nN]""")

/**
 * Parse a raw iCalendar `CATEGORIES` value (RFC 5545 §3.8.1.2) into a list of individual tokens.
 *
 * Values are comma-separated per RFC 5545 §3.3.11 (TEXT list), but commas escaped as `\,` belong to
 * the token. The 4 TEXT escapes defined by the RFC are decoded: `\\` → `\`, `\;` → `;`, `\,` → `,`,
 * `\n`/`\N` → newline. Any other `\X` sequence is not defined by the grammar and is left verbatim.
 * Each token is trimmed and blanks are dropped. Returns `null` when the property is absent or yields
 * no usable token, so "no categories" stays distinct from an empty list.
 */
private fun parseICalCategories(raw: String?): List<String>? {
    if (raw == null) return null
    return CATEGORY_TOKEN.findAll(raw)
        .map { it.value.unescapeIcalText().trim() }
        .filter(String::isNotEmpty)
        .toList()
        .takeIf(List<String>::isNotEmpty)
}

private fun String.unescapeIcalText(): String = TEXT_ESCAPE.replace(this) {
    if (it.value[1].equals('n', ignoreCase = true)) "\n" else it.value[1].toString()
}

