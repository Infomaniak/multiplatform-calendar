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

import com.infomaniak.multiplatform_calendar.core.data.local.entity.EventEntity
import com.infomaniak.multiplatform_calendar.core.data.remote.model.toCaldavHex
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTimeRange
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRuleSerializer
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateOnly
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.DateTimeUtc
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil.Floating
import com.infomaniak.multiplatform_calendar.core.extensions.toICalDate
import com.infomaniak.multiplatform_calendar.core.extensions.toICalLocalDateTime
import com.infomaniak.multiplatform_calendar.core.extensions.toICalUtcDateTime
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteColorChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteEventEdit
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange.Cleared
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange.Set
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteRecurrenceChange.Unchanged
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteVTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.format
import kotlinx.datetime.offsetIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal fun EventEditData.toRemoteEdit(stamp: String, previous: EventEntity?): RemoteEventEdit {
    val startZone = timing.start.serializationZone()
    val endZone = timing.end.serializationZone()
    return RemoteEventEdit(
        summary = title.ifBlank { null },
        dtStart = timing.start.toICal(timing.isAllDay, startZone),
        dtStartTzid = startZone.tzidForIcal(timing.isAllDay),
        dtEnd = timing.end.toICal(timing.isAllDay, endZone),
        dtEndTzid = endZone.tzidForIcal(timing.isAllDay),
        allDay = timing.isAllDay,
        location = location?.ifBlank { null },
        description = description?.ifBlank { null },
        transp = timeBlocking?.toIcalString(),
        timeZones = timing.vTimeZones(),
        colorChange = resolveColorChange(previous?.content?.colorArgb),
        recurrenceChange = resolveRecurrenceChange(previous?.rrule),
        alarms = resolveAlarmEdits(alarms, previous?.content?.alarms.orEmpty()),
        stamp = stamp,
    )
}

private fun EventEditData.resolveColorChange(previousColorArgb: Int?): RemoteColorChange = when {
    eventColor?.argb == previousColorArgb -> RemoteColorChange.Unchanged
    eventColor == null -> RemoteColorChange.Cleared
    else -> RemoteColorChange.Set(hex = eventColor.argb.toCaldavHex())
}

/**
 * Tri-state mirror of [resolveColorChange], with the rule's `UNTIL` first coerced to the value type
 * this edit's `DTSTART` requires (RFC 5545 §3.3.10) — otherwise toggling e.g. all-day would emit an
 * `RRULE` that [resolveRecurrence] rejects on reparse. Coercing before the equality check also lets a
 * type-only change re-emit the rule instead of being masked as [RemoteRecurrenceChange.Unchanged].
 */
private fun EventEditData.resolveRecurrenceChange(previousRule: RecurrenceRule?): RemoteRecurrenceChange {
    return when (val rule = timing.recurrenceRuleWithMatchingUntil()) {
        previousRule -> Unchanged
        null -> Cleared
        else -> Set(value = RecurrenceRuleSerializer.serialize(rule))
    }
}

/**
 * This timing's [recurrenceRule][EventTimeRange.recurrenceRule] (or `null`) with its `UNTIL` coerced to the
 * value type this timing's `DTSTART` requires (RFC 5545 §3.3.10): all-day → `DATE`, floating → local
 * `DATE-TIME`, otherwise UTC `DATE-TIME`. The calendar-face value is reinterpreted across forms (zone-free,
 * deterministic) rather than converted across zones.
 */
private fun EventTimeRange.recurrenceRuleWithMatchingUntil(): RecurrenceRule? {
    val rule = recurrenceRule ?: return null
    val current = rule.until ?: return rule
    val normalized = when {
        isAllDay -> DateOnly(current.calendarDate())
        startTimeZone == null -> Floating(current.calendarDateTime())
        else -> DateTimeUtc(current.calendarDateTime().toInstant(UTC))
    }
    return if (normalized == current) rule else rule.copy(until = normalized)
}

private fun RecurrenceUntil.calendarDate(): LocalDate = when (this) {
    is DateOnly -> date
    is Floating -> dateTime.date
    is DateTimeUtc -> instant.toLocalDateTime(UTC).date
}

private fun RecurrenceUntil.calendarDateTime(): LocalDateTime = when (this) {
    is DateOnly -> date.atTime(0, 0)
    is Floating -> dateTime
    is DateTimeUtc -> instant.toLocalDateTime(UTC)
}

/**
 * Serialize a calendar-face [LocalDateTime] as an RFC 5545 value:
 * - All-day      → `DATE` (`YYYYMMDD`).
 * - `zone` UTC   → FORM #2 (`...Z` suffix).
 * - `zone` set   → FORM #3 (no suffix; caller emits a `TZID` parameter alongside).
 * - `zone` null  → FORM #1 floating (no suffix, no `TZID`).
 */
private fun EventTiming.toICal(isAllDay: Boolean, serializationZone: TimeZone?): String = when {
    isAllDay -> localDateTime().date.toICalDate()
    this is EventTiming.Precised && serializationZone == UTC -> instant.toICalUtcDateTime()
    else -> localDateTime().toICalLocalDateTime()
}

/** The `TZID` parameter to emit alongside a `DATE-TIME` value, or `null` when none applies. */
private fun TimeZone?.tzidForIcal(isAllDay: Boolean): String? =
    if (isAllDay) null else explicitInIcal()?.id

/**
 * `VTIMEZONE` definitions to embed so the emitted iCalendar references self-contained zones
 * (RFC 5545 §3.6.5). Only FORM #3 (a real IANA `TZID`) needs one; all-day, UTC and floating
 * events emit no `TZID` and therefore need no `VTIMEZONE`.
 *
 * `DTSTART` and `DTEND` can reference different `TZID`s (RFC 5545 §3.8.2.2), so both zones are
 * emitted when distinct. Each offset is sampled at its own wall-clock — a single-offset
 * approximation that resolves this event's wall-clocks correctly everywhere (see [RemoteVTimeZone]).
 */
private fun EventTimeRange.vTimeZones(): List<RemoteVTimeZone> {
    if (isAllDay) return emptyList()
    val start = this.start.serializationZone().vTimeZone(startLocalDateTime)
    val end = this.end.serializationZone().vTimeZone(endLocalDateTime)
    return when {
        start == null && end == null -> emptyList()
        start != null && end != null && start.tzid == end.tzid -> listOf(start)
        else -> listOfNotNull(start, end)
    }
}

private fun TimeZone?.vTimeZone(local: LocalDateTime): RemoteVTimeZone? {
    val zone = explicitInIcal() ?: return null
    return RemoteVTimeZone(tzid = zone.id, offset = zone.icalOffsetAt(local))
}

/**
 * This zone if it must be referenced explicitly in iCal (RFC 5545 FORM #3 — a real IANA regional
 * zone), else `null`. Excludes:
 * - `null` (floating FORM #1 or all-day): no `TZID` parameter is emitted.
 * - `TimeZone.UTC` (FORM #2): the `Z` suffix is used instead of a `TZID`.
 *
 * Zones returned here are exactly those that require both a `TZID=` parameter on their
 * DATE-TIME value **and** a matching `VTIMEZONE` block in the emitted iCalendar.
 */
private fun TimeZone?.explicitInIcal(): TimeZone? = this?.takeUnless { it == UTC }

/**
 * TZID form cannot distinguish the two occurrences of a wall-clock inside a DST overlap. Use UTC
 * when this value is not the occurrence selected by kotlinx-datetime's deterministic local resolver.
 */
private fun EventTiming.serializationZone(): TimeZone? = when (this) {
    is EventTiming.Floating -> null
    is EventTiming.Precised -> {
        val local = instant.toLocalDateTime(timeZone)
        if (local.toInstant(timeZone) == instant) timeZone else UTC
    }
}

/**
 * Format the UTC offset valid at [local] in this zone as an RFC 5545 `TZOFFSETTO` value (e.g. "+0200").
 *
 * Uses [UtcOffset.Formats.FOUR_DIGITS] (always `±HHMM`, never `Z`, sub-minute LMT offsets
 * truncated) which matches RFC 5545 §3.3.14 `utc-offset = ("+" / "-") time-hour time-minute`.
 */
private fun TimeZone.icalOffsetAt(local: LocalDateTime): String =
    local.toInstant(this).offsetIn(this).format(UtcOffset.Formats.FOUR_DIGITS)
