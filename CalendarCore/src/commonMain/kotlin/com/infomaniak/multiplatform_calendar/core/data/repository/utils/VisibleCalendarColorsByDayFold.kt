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
    val colorOrderByDay = LinkedHashMap<LocalDate, MutableMap<CalendarId, DayCalendarColorSortData>>()
    val timeZoneCache = HashMap<String, TimeZone>()
    val occurrences = ArrayList<Occurrence>() // Reused buffer for recurring expansion
    val visibleDays = fromDay..toDay

    for (row in this@foldToDailyCalendarColors) {
        currentCoroutineContext().ensureActive()

        val colors = colorsBySourceColor.getOrPut(row.colorArgb) { CalendarColors.from(row.colorArgb) }

        val startZone = row.startZoneId?.let { id -> timeZoneCache.getOrPut(id) { TimeZone.of(id) } }
        val endZone = row.endZoneId?.let { id -> timeZoneCache.getOrPut(id) { TimeZone.of(id) } }
        val timing = EventTiming(
            start = row.dtStart,
            end = row.dtEndEffective,
            startTimeZone = startZone,
            endTimeZone = endZone,
            isAllDay = row.isAllDay,
            recurrenceRule = row.rrule,
        )

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
            val startLocalDateTime = row.dtStart.projectInto(startZone, timeZone)
            val endLocalDateTime = row.dtEndEffective.projectInto(endZone, timeZone)
            val firstDay = startLocalDateTime.date
            val lastDay = endLocalDateTime.lastInclusiveDay(notBefore = firstDay)
            colorOrderByDay.recordCalendarColorForCoveredDays(
                firstDay = firstDay,
                lastDay = lastDay,
                visibleDays = visibleDays,
                calendarId = row.calendarId,
                color = colors,
                firstDaySortKey = DayColorSortKey(
                    isAllDay = row.isAllDay,
                    displayStart = startLocalDateTime,
                    occurrenceSortId = row.eventId.url,
                ),
            )
            continue
        }

        val overridesByOccurrenceKey = row.overrides.associateBy { it.recurrenceKey.canonical }
        occurrences.forEach { occurrence ->
            if (occurrence.key.canonical in overridesByOccurrenceKey) return@forEach
            val startLocalDateTime = occurrence.start.projectInto(occurrence.startTimeZone, timeZone)
            val endLocalDateTime = occurrence.end.projectInto(occurrence.endTimeZone, timeZone)
            val firstDay = startLocalDateTime.date
            val lastDay = endLocalDateTime.lastInclusiveDay(notBefore = firstDay)
            colorOrderByDay.recordCalendarColorForCoveredDays(
                firstDay = firstDay,
                lastDay = lastDay,
                visibleDays = visibleDays,
                calendarId = row.calendarId,
                color = colors,
                firstDaySortKey = DayColorSortKey(
                    isAllDay = occurrence.isAllDay,
                    displayStart = startLocalDateTime,
                    occurrenceSortId = "${row.eventId.url}#${occurrence.key.canonical}",
                ),
            )
        }

        row.overrides.forEach { override ->
            if (override.status == EventStatus.CANCELLED) return@forEach
            val startZone = override.startTimeZone?.let { id -> timeZoneCache.getOrPut(id) { TimeZone.of(id) } }
            val endZone = override.endTimeZone?.let { id -> timeZoneCache.getOrPut(id) { TimeZone.of(id) } }
            val startLocalDateTime = override.dtStart.projectInto(startZone, timeZone)
            val endLocalDateTime = override.dtEndEffective.projectInto(endZone, timeZone)
            // The relation carries *every* override of the master, and the range branches are a deliberate
            // superset, so re-apply the `[rangeStart, rangeEnd[` overlap rule the planning flow uses.
            if (startLocalDateTime.toInstant(timeZone) >= rangeEnd || endLocalDateTime.toInstant(timeZone) <= rangeStart) {
                return@forEach
            }
            val firstDay = startLocalDateTime.date
            val lastDay = endLocalDateTime.lastInclusiveDay(notBefore = firstDay)
            colorOrderByDay.recordCalendarColorForCoveredDays(
                firstDay = firstDay,
                lastDay = lastDay,
                visibleDays = visibleDays,
                calendarId = row.calendarId,
                color = colors,
                firstDaySortKey = DayColorSortKey(
                    isAllDay = override.isAllDay,
                    displayStart = startLocalDateTime,
                    occurrenceSortId = "${row.eventId.url}#${override.recurrenceKey.canonical}",
                ),
            )
        }
    }

    return colorOrderByDay.mapValues { (_, dataByCalendarId) ->
        // Step 2 (final ordering): we now have one key per calendar for that day (the earliest event for
        // that calendar). Sort entries by that key so output order mirrors planning's per-day event order.
        dataByCalendarId.entries
            .sortedWith(
                compareBy<Map.Entry<CalendarId, DayCalendarColorSortData>>(
                    { it.value.sortKey },
                    { it.key.url },
                ),
            ).map { (calendarId, data) ->
                VisibleCalendarColor(
                    id = calendarId,
                    colors = data.calendarColors,
                )
            }
    }
}

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

private fun MutableMap<LocalDate, MutableMap<CalendarId, DayCalendarColorSortData>>.recordCalendarColorForCoveredDays(
    firstDay: LocalDate,
    lastDay: LocalDate,
    visibleDays: ClosedRange<LocalDate>,
    calendarId: CalendarId,
    color: CalendarColors,
    firstDaySortKey: DayColorSortKey,
) {
    val from = maxOf(firstDay, visibleDays.start)
    val to = minOf(lastDay, visibleDays.endInclusive)
    if (from > to) return

    var day = from
    while (day <= to) {
        val sortKey = if (day == firstDay) {
            firstDaySortKey
        } else {
            DayColorSortKey(
                isAllDay = firstDaySortKey.isAllDay,
                displayStart = LocalDateTime(day, MIDNIGHT),
                occurrenceSortId = firstDaySortKey.occurrenceSortId,
            )
        }

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

