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
import com.infomaniak.multiplatform_calendar.core.domain.model.event.DateListEdit
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventEditData
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toLocalStart
import com.infomaniak.multiplatform_calendar.core.domain.model.event.toRecurrenceKey
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.IcalDateValue
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
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDateListChange
import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.RemoteDateListLine
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

/**
 * [exDates]/[rDates] stay out of [EventEditData] on purpose — only occurrence-level operations touch a
 * series' recurrence set, so a plain edit leaves it alone (see [DateListEdit]).
 */
internal fun EventEditData.toRemoteEdit(
    stamp: String,
    previous: EventEntity?,
    exDates: DateListEdit = DateListEdit.Preserve,
    rDates: DateListEdit = DateListEdit.Preserve,
): RemoteEventEdit {
    val startZone = timing.startTimeZone
    val endZone = timing.endTimeZone
    return RemoteEventEdit(
        summary = title.ifBlank { null },
        dtStart = timing.start.toICal(timing.isAllDay, startZone),
        dtStartTzid = startZone.tzidForIcal(timing.isAllDay),
        dtEnd = timing.end.toICal(timing.isAllDay, endZone),
        dtEndTzid = endZone.tzidForIcal(timing.isAllDay),
        allDay = timing.isAllDay,
        location = location?.ifBlank { null },
        description = description?.ifBlank { null },
        timeZones = timing.vTimeZones(),
        colorChange = resolveColorChange(previous?.colorArgb),
        recurrenceChange = resolveRecurrenceChange(previous?.rrule),
        exDateChange = timing.resolveDateListChange(exDates, previous, previous?.exDates),
        rDateChange = timing.resolveDateListChange(rDates, previous, previous?.rDates),
        alarms = resolveAlarmEdits(alarms, previous?.alarms.orEmpty()),
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
 * This timing's [recurrenceRule][EventTiming.recurrenceRule] (or `null`) with its `UNTIL` coerced to the
 * value type this timing's `DTSTART` requires (RFC 5545 §3.3.10): all-day → `DATE`, floating → local
 * `DATE-TIME`, otherwise UTC `DATE-TIME`. The calendar-face value is reinterpreted across forms (zone-free,
 * deterministic) rather than converted across zones.
 */
private fun EventTiming.recurrenceRuleWithMatchingUntil(): RecurrenceRule? {
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
 * Tri-state `EXDATE`/`RDATE` change. [DateListEdit.Set] values are first coerced to the value type this
 * edit's `DTSTART` requires (see [coercedToDtStartForm]) — [resolveRecurrence] rejects a mismatched form
 * on reparse, and coercing before the equality check lets a type-only change re-emit instead of being
 * masked as [RemoteDateListChange.Unchanged].
 *
 * All values then share one form and one zone, so a single line is enough (RFC 5545 §3.2.19).
 */
private fun EventTiming.resolveDateListChange(
    edit: DateListEdit,
    previous: EventEntity?,
    previousValues: List<IcalDateValue>?,
): RemoteDateListChange {
    val values = when (edit) {
        DateListEdit.Preserve -> return RemoteDateListChange.Unchanged
        DateListEdit.Clear -> emptyList()
        is DateListEdit.Set -> edit.values
    }
    // The values designate occurrences of the master as it stands *before* this edit.
    val reference = previous?.timing?.toDomain() ?: this
    val coerced = values.mapNotNull { it.coercedToDtStartForm(reference, isAllDay, startTimeZone) }
    return when {
        coerced == previousValues.orEmpty() -> RemoteDateListChange.Unchanged
        coerced.isEmpty() -> RemoteDateListChange.Cleared
        else -> RemoteDateListChange.Set(
            lines = listOf(
                RemoteDateListLine(
                    tzid = startTimeZone.tzidForIcal(isAllDay),
                    isDateOnly = isAllDay,
                    values = coerced.map { it.calendarDateTime().toICal(isAllDay, startTimeZone) },
                ),
            ),
        )
    }
}

/**
 * This value re-expressed in the form a `DTSTART` described by [isAllDay]/[zone] requires, still
 * designating the occurrence it designated on [reference] (the master as stored before this edit).
 *
 * A value only excludes — or adds — an occurrence when it equals that occurrence's start, so the
 * emitted form has to preserve the wall-clock [reference] resolved it to. Reading the value's own face
 * instead would silently move it: a date-only value would land on midnight rather than the master's
 * time of day, and a value carrying a foreign `TZID` would be reinterpreted in the master's zone.
 *
 * Returns `null` when the value designates nothing on [reference] — the expander ignores it too.
 */
private fun IcalDateValue.coercedToDtStartForm(
    reference: EventTiming,
    isAllDay: Boolean,
    zone: TimeZone?,
): IcalDateValue? {
    val face = toRecurrenceKey(reference)?.toLocalStart(reference, defaultZone = UTC) ?: return null
    return when {
        isAllDay -> IcalDateValue.AllDay(face.date)
        zone == null -> IcalDateValue.Floating(face)
        else -> IcalDateValue.Zoned(face.toInstant(zone), zone.id)
    }
}

private fun IcalDateValue.calendarDateTime(): LocalDateTime = when (this) {
    is IcalDateValue.AllDay -> date.atTime(0, 0)
    is IcalDateValue.Floating -> localDateTime
    is IcalDateValue.Zoned -> instant.toLocalDateTime(TimeZone.of(timeZoneId))
}

/**
 * Serialize a calendar-face [LocalDateTime] as an RFC 5545 value:
 * - All-day      → `DATE` (`YYYYMMDD`).
 * - `zone` UTC   → FORM #2 (`...Z` suffix).
 * - `zone` set   → FORM #3 (no suffix; caller emits a `TZID` parameter alongside).
 * - `zone` null  → FORM #1 floating (no suffix, no `TZID`).
 */
private fun LocalDateTime.toICal(isAllDay: Boolean, zone: TimeZone?): String = when {
    isAllDay -> date.toICalDate()
    zone == UTC -> toInstant(UTC).toICalUtcDateTime()
    else -> toICalLocalDateTime()
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
private fun EventTiming.vTimeZones(): List<RemoteVTimeZone> {
    if (isAllDay) return emptyList()
    val start = startTimeZone.vTimeZone(start)
    val end = endTimeZone.vTimeZone(end)
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
 * Format the UTC offset valid at [local] in this zone as an RFC 5545 `TZOFFSETTO` value (e.g. "+0200").
 *
 * Uses [UtcOffset.Formats.FOUR_DIGITS] (always `±HHMM`, never `Z`, sub-minute LMT offsets
 * truncated) which matches RFC 5545 §3.3.14 `utc-offset = ("+" / "-") time-hour time-minute`.
 */
private fun TimeZone.icalOffsetAt(local: LocalDateTime): String =
    local.toInstant(this).offsetIn(this).format(UtcOffset.Formats.FOUR_DIGITS)
