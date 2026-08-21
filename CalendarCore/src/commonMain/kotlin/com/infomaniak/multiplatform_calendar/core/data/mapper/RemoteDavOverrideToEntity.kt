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
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventContentEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventOverrideEntity
import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventTimingEntity
import com.infomaniak.multiplatform_calendar.core.data.mapper.timezone.resolveTimeZone
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseCss3ColorName
import com.infomaniak.multiplatform_calendar.core.data.remote.model.parseHexColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.Classification
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.MasterTiming
import com.infomaniak.multiplatform_calendar.core.extensions.isICalDateOnly
import com.infomaniak.multiplatform_calendar.core.extensions.parseICalDateTime
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEvent
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDavEventOverride
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteIcalDateValueType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Map this event's detached instances into [EventOverrideEntity] rows bound to [masterTiming].
 *
 * A malformed sibling is skipped on its own rather than costing the whole series.
 */
internal fun RemoteDavEvent.toOverrideEntities(masterTiming: EventTimingEntity): List<EventOverrideEntity> {
    val masterId = EventId(url)
    return overrides.mapNotNull { override ->
        runCatching { override.toEntity(masterId, url, masterTiming) }.getOrNull()
    }
}

@Throws(CaldavParsingException::class)
private fun RemoteDavEventOverride.toEntity(
    masterId: EventId,
    url: String,
    masterTiming: EventTimingEntity,
): EventOverrideEntity {
    val original = resolveOriginalPosition(url, masterTiming)

    return EventOverrideEntity(
        masterId = masterId,
        recurrenceKey = original.key,
        originalStartInstantMs = original.startInstantMs,
        originalEndInstantMs = original.endInstantMs,
        originalStartLocalDateTime = original.startLocalDateTime,
        originalEndLocalDateTime = original.endLocalDateTime,
        content = EventContentEntity(
            summary = summary ?: "",
            description = description,
            location = location,
            timing = toTimingEntity(url),
            created = parseICalDateTime(created),
            lastModified = parseICalDateTime(lastModified),
            dtStamp = parseICalDateTime(dtstamp),
            status = EventStatus.fromIcalString(status),
            transp = transp,
            classification = Classification.fromIcalString(classification),
            priority = priority?.toIntOrNull(),
            sequence = sequence?.toIntOrNull(),
            categories = parseICalCategories(categories),
            attendees = attendees.map { it.toEntity() },
            organizer = organizer?.toEntity(),
            alarms = alarms.map { it.toEntity() },
            colorArgb = parseHexColor(colorHex) ?: parseCss3ColorName(colorIcalName),
            colorIcalName = colorIcalName,
        ),
    )
}

private class OriginalPosition(
    val key: RecurrenceKey,
    val startLocalDateTime: LocalDateTime,
    val endLocalDateTime: LocalDateTime,
    val startInstantMs: Long?,
    val endInstantMs: Long?,
)

/**
 * Resolve the `RECURRENCE-ID` into the position of the instance it replaces.
 *
 * RFC 5545 §3.8.4.4 pins its value *type* to `DTSTART`'s and its floating-ness to it too, but leaves
 * the zone free: the raw value is anchored to an instant then re-expressed in the master's own form,
 * as the expander does for `EXDATE`/`RDATE`. Keying on the raw wall-clock would orphan a foreign zone.
 */
@Throws(CaldavParsingException::class)
private fun RemoteDavEventOverride.resolveOriginalPosition(
    url: String,
    masterTiming: EventTimingEntity,
): OriginalPosition {
    val raw = parseICalDateTime(recurrenceId)
        ?: throw CaldavParsingException("Unparsable RECURRENCE-ID '$recurrenceId' for event $url")
    val isDateValued = recurrenceIdValueType == RemoteIcalDateValueType.Date || isICalDateOnly(recurrenceId)
    if (isDateValued != masterTiming.isAllDay) throw CaldavParsingException("RECURRENCE-ID form differs from DTSTART's")

    val masterZone = masterTiming.startTimeZone?.let(TimeZone::of)
    // A bare ID inherits the master's zone: non-conformant too, but its intent stays unambiguous.
    val recurrenceIdZone = resolveTimeZone(
        isAllDay = isDateValued,
        rawValue = recurrenceId,
        tzid = recurrenceIdTzid,
        eventUrl = url,
        propertyName = "RECURRENCE-ID",
    ) ?: masterZone
    // RFC 5545 §3.8.4.4 pairs the RECURRENCE-ID form with DTSTART's, and `Z` anchors just like a TZID:
    // an anchored ID cannot name an instance of a floating master. The expander drops anchored
    // EXDATE/RDATEs for the same reason.
    if (masterZone == null && recurrenceIdZone != null) {
        throw CaldavParsingException("Anchored RECURRENCE-ID cannot identify an instance of a floating master")
    }

    // The expander's own timing model, so a stored position cannot disagree with the occurrence it replaces.
    val master = MasterTiming.of(masterTiming.toDomain(), defaultZone = TimeZone.UTC)
    val startLocalDateTime = when {
        masterTiming.isAllDay -> LocalDateTime(raw.date, masterTiming.dtStart.time)
        recurrenceIdZone == null -> raw
        else -> raw.toInstant(recurrenceIdZone).toLocalDateTime(master.startZone)
    }
    val startInstant = master.resolvedStartInstant(startLocalDateTime)
    val (endLocalDateTime, endInstant) = master.occurrenceEnd(startLocalDateTime, startInstant)
    val isAnchored = masterTiming.isAllDay || masterZone != null

    return OriginalPosition(
        key = recurrenceKeyOf(startLocalDateTime, startInstant, masterTiming),
        startLocalDateTime = startLocalDateTime,
        endLocalDateTime = endLocalDateTime,
        startInstantMs = startInstant.toEpochMilliseconds().takeIf { isAnchored },
        endInstantMs = endInstant.toEpochMilliseconds().takeIf { isAnchored },
    )
}

/** Typed after the master's `DTSTART` form (RFC 5545 §3.8.4.4). */
private fun recurrenceKeyOf(
    startLocalDateTime: LocalDateTime,
    startInstant: kotlin.time.Instant,
    masterTiming: EventTimingEntity,
): RecurrenceKey = when {
    masterTiming.isAllDay -> RecurrenceKey.AllDay(startLocalDateTime.date)
    masterTiming.startTimeZone == TimeZone.UTC.id -> RecurrenceKey.Utc(startInstant)
    masterTiming.startTimeZone != null -> RecurrenceKey.Zoned(startLocalDateTime, masterTiming.startTimeZone)
    else -> RecurrenceKey.Floating(startLocalDateTime)
}
