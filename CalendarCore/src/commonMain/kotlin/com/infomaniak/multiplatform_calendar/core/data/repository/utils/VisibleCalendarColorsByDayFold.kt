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
package com.infomaniak.multiplatform_calendar.core.data.repository.utils

import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventCalendarColorInRange
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarColors
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.VisibleCalendarColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.comparePerDayDisplayOrder
import com.infomaniak.multiplatform_calendar.core.domain.model.event.expandRecurrenceOccurrencesInWindow
import com.infomaniak.multiplatform_calendar.core.domain.model.event.lastInclusiveDay
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.Occurrence
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionLimits
import com.infomaniak.multiplatform_calendar.core.domain.recurrence.ExpansionOutcome
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Fold the lightweight [EventCalendarColorInRange] rows into `day -> visible calendar colors` over
 * `[rangeStart, rangeEnd[` (in [timeZone]).
 *
 * Only days that actually own events are kept; each maps to the [VisibleCalendarColor] entries of the calendars having
 * at least one event that day. Non-recurring rows are handled as direct spans; recurring masters are expanded into
 * occurrences (same expander as planning) and then each occurrence span is folded by day. This keeps RRULE parity with
 * the planning day-slice flow while staying lightweight (projection rows only, no full domain event graph).
 *
 * Overrides are substituted exactly as in the planning flow, and for the same reason: the two views must agree on
 * which day owns an instance. A slot carrying an override is skipped, and the override dots its own days instead,
 * unless it is `STATUS:CANCELLED`. A master whose recurrence was suspended never reaches that step, so its stale
 * overrides stay hidden.
 *
 * Per-day color order mirrors planning's event order: all-day first, then by slice display start time, then by a stable
 * occurrence id. For each day+calendar, the earliest event for that calendar defines its position.
 */
internal suspend fun List<EventCalendarColorInRange>.foldToDailyCalendarColors(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
    onExpansionTruncated: (masterId: EventId, outcome: ExpansionOutcome) -> Unit = { _, _ -> },
    onInvalidRange: (rangeStart: Instant, rangeEnd: Instant, timeZone: TimeZone, fromDay: LocalDate, toDay: LocalDate) -> Unit = { _, _, _, _, _ -> },
): Map<LocalDate, List<VisibleCalendarColor>> {
    val fromDay = rangeStart.toLocalDateTime(timeZone).date
    val toDay = rangeEnd.toLocalDateTime(timeZone).lastInclusiveDay(notBefore = fromDay)
    if (fromDay > toDay) {
        onInvalidRange(rangeStart, rangeEnd, timeZone, fromDay, toDay)
        return emptyMap()
    }

    val colorsBySourceColor = HashMap<Int?, CalendarColors>()
    val colorOrderByDay: ColorOrderByDay = LinkedHashMap()
    val zoneCache = HashMap<String, TimeZone>()
    val occurrences = ArrayList<Occurrence>() // Reused buffer for recurring expansion
    val visibleDays = fromDay..toDay

    for (row in this@foldToDailyCalendarColors) {
        currentCoroutineContext().ensureActive()

        val colors = colorsBySourceColor.getOrPut(row.colorArgb) { CalendarColors.from(row.colorArgb) }
        val timing = row.toTiming(zoneCache)

        occurrences.clear()
        val hasRecurringExpansion = timing.expandRecurrenceOccurrencesInWindow(
            masterId = row.eventId,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            timeZone = timeZone,
            target = occurrences,
            limits = limits,
            onExpansionTruncated = onExpansionTruncated,
        )
        if (!hasRecurringExpansion) {
            colorOrderByDay.recordPlainEvent(row, timing, colors, visibleDays, timeZone)
            continue
        }

        val overriddenKeys = row.overrides.mapTo(HashSet()) { it.recurrenceKey.canonical }
        colorOrderByDay.recordRuleOccurrences(row, occurrences, overriddenKeys, colors, visibleDays, timeZone)
        colorOrderByDay.recordOverriddenInstances(row, colors, zoneCache, visibleDays, rangeStart, rangeEnd, timeZone)
    }

    return colorOrderByDay.toColorsByDay()
}

/** The [EventTiming] this row describes, resolving its zone ids through [zoneCache]. */
private fun EventCalendarColorInRange.toTiming(zoneCache: MutableMap<String, TimeZone>) = EventTiming(
    start = dtStart,
    end = dtEndEffective,
    startTimeZone = startZoneId?.let { zoneCache.zoneOf(it) },
    endTimeZone = endZoneId?.let { zoneCache.zoneOf(it) },
    isAllDay = isAllDay,
    recurrenceRule = rrule,
)

private fun MutableMap<String, TimeZone>.zoneOf(id: String): TimeZone = getOrPut(id) { TimeZone.of(id) }

/** Record a non-recurring event, whose single span is [timing] itself. */
private fun ColorOrderByDay.recordPlainEvent(
    row: EventCalendarColorInRange,
    timing: EventTiming,
    color: CalendarColors,
    visibleDays: ClosedRange<LocalDate>,
    timeZone: TimeZone,
) {
    recordCoveredDays(
        start = timing.start.projectInto(timing.startTimeZone, timeZone),
        end = timing.end.projectInto(timing.endTimeZone, timeZone),
        visibleDays = visibleDays,
        calendarId = row.calendarId,
        color = color,
        isAllDay = row.isAllDay,
        occurrenceSortId = row.eventId.url,
    )
}

/**
 * Record the occurrences the rule generated, skipping every slot whose key is in [overriddenKeys]:
 * the override *is* that instance, and [recordOverriddenInstances] records it on its own days.
 */
private suspend fun ColorOrderByDay.recordRuleOccurrences(
    row: EventCalendarColorInRange,
    occurrences: List<Occurrence>,
    overriddenKeys: Set<String>,
    color: CalendarColors,
    visibleDays: ClosedRange<LocalDate>,
    timeZone: TimeZone,
) {
    for (occurrence in occurrences) {
        currentCoroutineContext().ensureActive()
        if (occurrence.key.canonical in overriddenKeys) continue
        recordCoveredDays(
            start = occurrence.start.projectInto(occurrence.startTimeZone, timeZone),
            end = occurrence.end.projectInto(occurrence.endTimeZone, timeZone),
            visibleDays = visibleDays,
            calendarId = row.calendarId,
            color = color,
            isAllDay = occurrence.isAllDay,
            occurrenceSortId = OccurrenceId.of(row.eventId, occurrence.key).value,
        )
    }
}

/**
 * Record each override on the days it actually lands on, which may differ from the slot it replaces.
 *
 * A `STATUS:CANCELLED` override is dropped instead: [recordRuleOccurrences] already left its slot
 * undotted, so dropping it here is what leaves that single occurrence deleted.
 */
private suspend fun ColorOrderByDay.recordOverriddenInstances(
    row: EventCalendarColorInRange,
    color: CalendarColors,
    zoneCache: MutableMap<String, TimeZone>,
    visibleDays: ClosedRange<LocalDate>,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
) {
    for (override in row.overrides) {
        currentCoroutineContext().ensureActive()
        if (override.status == EventStatus.CANCELLED) continue

        val start = override.dtStart.projectInto(override.startTimeZone?.let { zoneCache.zoneOf(it) }, timeZone)
        val end = override.dtEndEffective.projectInto(override.endTimeZone?.let { zoneCache.zoneOf(it) }, timeZone)
        // The relation carries *every* override of the master, and the range branches are a deliberate
        // superset, so re-apply the `[rangeStart, rangeEnd[` overlap rule the planning flow uses.
        if (start.toInstant(timeZone) >= rangeEnd || end.toInstant(timeZone) <= rangeStart) continue

        recordCoveredDays(
            start = start,
            end = end,
            visibleDays = visibleDays,
            calendarId = row.calendarId,
            color = color,
            isAllDay = override.isAllDay,
            occurrenceSortId = OccurrenceId.of(row.eventId, override.recurrenceKey).value,
        )
    }
}

/**
 * Step 2 (final ordering): each day now holds one key per calendar, the earliest event of that
 * calendar. Sort on it so the output mirrors planning's per-day event order.
 */
private fun ColorOrderByDay.toColorsByDay(): Map<LocalDate, List<VisibleCalendarColor>> {
    return mapValues { (_, dataByCalendarId) ->
        dataByCalendarId.entries
            .sortedWith(
                compareBy<Map.Entry<CalendarId, DayCalendarColorSortData>>(
                    { it.value.sortKey },
                    { it.key.url },
                ),
            ).map { (calendarId, data) -> VisibleCalendarColor(id = calendarId, colors = data.calendarColors) }
    }
}

private typealias ColorOrderByDay = MutableMap<LocalDate, MutableMap<CalendarId, DayCalendarColorSortData>>

private data class DayCalendarColorSortData(
    val calendarColors: CalendarColors,
    val sortKey: DayColorSortKey,
)

private data class DayColorSortKey(
    val isAllDay: Boolean,
    val displayStart: LocalDateTime,
    val occurrenceSortId: String,
) : Comparable<DayColorSortKey> {
    override fun compareTo(other: DayColorSortKey): Int {
        return comparePerDayDisplayOrder(
            leftIsAllDay = isAllDay,
            leftDisplayStart = displayStart,
            leftOccurrenceSortId = occurrenceSortId,
            rightIsAllDay = other.isAllDay,
            rightDisplayStart = other.displayStart,
            rightOccurrenceSortId = other.occurrenceSortId,
        )
    }
}

/**
 * Record [color] for [calendarId] on every visible day the `[start, end]` span covers, keying the
 * first day on the event itself and the following ones on midnight.
 */
private fun ColorOrderByDay.recordCoveredDays(
    start: LocalDateTime,
    end: LocalDateTime,
    visibleDays: ClosedRange<LocalDate>,
    calendarId: CalendarId,
    color: CalendarColors,
    isAllDay: Boolean,
    occurrenceSortId: String,
) {
    val firstDay = start.date
    val lastDay = end.lastInclusiveDay(notBefore = firstDay)
    val from = maxOf(firstDay, visibleDays.start)
    val to = minOf(lastDay, visibleDays.endInclusive)
    if (from > to) return

    var day = from
    while (day <= to) {
        val displayStart = if (day == firstDay) start else LocalDateTime(day, MIDNIGHT)
        val sortKey = DayColorSortKey(isAllDay, displayStart, occurrenceSortId)

        val dataByCalendarId = getOrPut(day) { LinkedHashMap() }
        val previous = dataByCalendarId[calendarId]
        // Step 1 (per-calendar reduction): a day can contain multiple events from the same calendar.
        // Keep only the earliest event key for that calendar (min sort key), because this key drives
        // the final per-day ordering once all events have been folded.
        if (previous == null || sortKey < previous.sortKey) {
            dataByCalendarId[calendarId] = DayCalendarColorSortData(calendarColors = color, sortKey = sortKey)
        }

        day = day.plus(1, DateTimeUnit.DAY)
    }
}

private val MIDNIGHT = LocalTime(0, 0)

/**
 * Reproject a stored wall-clock into [targetZone], matching `EventTiming.startIn`/`endIn`:
 * a `null` source zone (floating or all-day) is interpreted directly in [targetZone]; any other zone is
 * reprojected through an absolute instant.
 */
private fun LocalDateTime.projectInto(sourceZone: TimeZone?, targetZone: TimeZone): LocalDateTime {
    if (sourceZone == null || sourceZone == targetZone) return this
    return toInstant(sourceZone).toLocalDateTime(targetZone)
}

