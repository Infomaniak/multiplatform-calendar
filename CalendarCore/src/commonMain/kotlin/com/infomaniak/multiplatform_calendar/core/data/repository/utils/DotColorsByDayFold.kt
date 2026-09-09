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

import com.infomaniak.multiplatform_calendar.core.data.local.projection.EventDotColorInRange
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.CalendarId
import com.infomaniak.multiplatform_calendar.core.domain.model.calendar.DotColor
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventStatus
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import com.infomaniak.multiplatform_calendar.core.domain.model.event.OccurrenceId
import com.infomaniak.multiplatform_calendar.core.domain.model.event.SeriesEndFilter
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
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
 * Fold the lightweight [EventDotColorInRange] rows into `day -> dot colors` over `[rangeStart, rangeEnd[`
 * (in [timeZone]).
 *
 * Only days that actually own events are kept; each maps to the [DotColor] entries of that day, reduced per
 * calendar **and** per color (see [DotKey]). Non-recurring rows are handled as direct spans; recurring masters
 * are expanded into occurrences (same expander as planning) and then each occurrence span is folded by day.
 * This keeps RRULE parity with the planning day-slice flow while staying lightweight (projection rows only, no
 * full domain event graph).
 *
 * Overrides are substituted exactly as in the planning flow, and for the same reason: the two views must agree on
 * which day owns an instance. A slot carrying an override is skipped, and the override dots its own days — with
 * its own color — instead, unless it is `STATUS:CANCELLED`. A master whose recurrence was suspended never
 * reaches that step, so its stale overrides stay hidden.
 *
 * Per-day dot order mirrors planning's event order: all-day first, then by slice display start time, then by a
 * stable occurrence id. For each day+calendar+color, the earliest event of that pair defines its position.
 */
internal suspend fun List<EventDotColorInRange>.foldToDailyDotColors(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    limits: ExpansionLimits = ExpansionLimits(),
    onExpansionTruncated: (masterId: EventId, outcome: ExpansionOutcome) -> Unit = { _, _ -> },
    onInvalidRange: (rangeStart: Instant, rangeEnd: Instant, timeZone: TimeZone, fromDay: LocalDate, toDay: LocalDate) -> Unit = { _, _, _, _, _ -> },
    onOrphanOverrideDropped: (masterId: EventId, slot: RecurrenceKey) -> Unit = { _, _ -> },
): Map<LocalDate, List<DotColor>> {
    val fromDay = rangeStart.toLocalDateTime(timeZone).date
    val toDay = rangeEnd.toLocalDateTime(timeZone).lastInclusiveDay(notBefore = fromDay)
    if (fromDay > toDay) {
        onInvalidRange(rangeStart, rangeEnd, timeZone, fromDay, toDay)
        return emptyMap()
    }

    val dotOrderByDay: DotOrderByDay = LinkedHashMap()
    val zoneCache = HashMap<String, TimeZone>()
    val occurrences = ArrayList<Occurrence>() // Reused buffer for recurring expansion
    val visibleDays = fromDay..toDay

    for (row in this@foldToDailyDotColors) {
        currentCoroutineContext().ensureActive()

        val dotKey = DotKey(row.calendarId, DotColor.from(row.calendarColorArgb))
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
            dotOrderByDay.recordPlainEvent(row, timing, dotKey, visibleDays, timeZone)
            continue
        }

        val overriddenKeys = row.overrides.mapTo(HashSet()) { it.recurrenceKey.canonical }
        dotOrderByDay.recordRuleOccurrences(row, occurrences, overriddenKeys, dotKey, visibleDays, timeZone)
        dotOrderByDay.recordOverriddenInstances(
            row = row,
            seriesEnd = SeriesEndFilter.of(timing, timeZone),
            dotKey = dotKey,
            zoneCache = zoneCache,
            visibleDays = visibleDays,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            timeZone = timeZone,
            onOrphanOverrideDropped = onOrphanOverrideDropped,
        )
    }

    return dotOrderByDay.toDotColorsByDay()
}

/** The [EventTiming] this row describes, resolving its zone ids through [zoneCache]. */
private fun EventDotColorInRange.toTiming(zoneCache: MutableMap<String, TimeZone>) = EventTiming(
    start = dtStart,
    end = dtEndEffective,
    startTimeZone = startZoneId?.let { zoneCache.zoneOf(it) },
    endTimeZone = endZoneId?.let { zoneCache.zoneOf(it) },
    isAllDay = isAllDay,
    recurrenceRule = rrule,
    rDates = rDates,
    exDates = exDates,
)

private fun MutableMap<String, TimeZone>.zoneOf(id: String): TimeZone = getOrPut(id) { TimeZone.of(id) }

/** Record a non-recurring event, whose single span is [timing] itself. */
private fun DotOrderByDay.recordPlainEvent(
    row: EventDotColorInRange,
    timing: EventTiming,
    dotKey: DotKey,
    visibleDays: ClosedRange<LocalDate>,
    timeZone: TimeZone,
) {
    recordCoveredDays(
        start = timing.start.projectInto(timing.startTimeZone, timeZone),
        end = timing.end.projectInto(timing.endTimeZone, timeZone),
        visibleDays = visibleDays,
        dotKey = dotKey,
        isAllDay = row.isAllDay,
        occurrenceSortId = row.eventId.url,
    )
}

/**
 * Record the occurrences the rule generated, skipping every slot whose key is in [overriddenKeys]:
 * the override *is* that instance, and [recordOverriddenInstances] records it on its own days.
 */
private suspend fun DotOrderByDay.recordRuleOccurrences(
    row: EventDotColorInRange,
    occurrences: List<Occurrence>,
    overriddenKeys: Set<String>,
    dotKey: DotKey,
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
            dotKey = dotKey,
            isAllDay = occurrence.isAllDay,
            occurrenceSortId = OccurrenceId.Recurrence(row.eventId, occurrence.key).value,
        )
    }
}

/**
 * Record each override on the days it actually lands on, which may differ from the slot it replaces.
 *
 * A `STATUS:CANCELLED` override is dropped instead: [recordRuleOccurrences] already left its slot
 * undotted, so dropping it here is what leaves that single occurrence deleted. An override whose slot the
 * series no longer holds is dropped too, so a day never gets a dot planning would not show.
 */
private suspend fun DotOrderByDay.recordOverriddenInstances(
    row: EventDotColorInRange,
    seriesEnd: SeriesEndFilter?,
    dotKey: DotKey,
    zoneCache: MutableMap<String, TimeZone>,
    visibleDays: ClosedRange<LocalDate>,
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    onOrphanOverrideDropped: (masterId: EventId, slot: RecurrenceKey) -> Unit,
) {
    for (override in row.overrides) {
        currentCoroutineContext().ensureActive()
        if (override.status == EventStatus.CANCELLED) continue
        if (seriesEnd?.isOrphan(override.recurrenceKey) == true) {
            onOrphanOverrideDropped(row.eventId, override.recurrenceKey)
            continue
        }

        val start = override.dtStart.projectInto(override.startTimeZone?.let { zoneCache.zoneOf(it) }, timeZone)
        val end = override.dtEndEffective.projectInto(override.endTimeZone?.let { zoneCache.zoneOf(it) }, timeZone)
        // The relation carries *every* override of the master, and the range branches are a deliberate
        // superset, so re-apply the `[rangeStart, rangeEnd[` overlap rule the planning flow uses.
        if (start.toInstant(timeZone) >= rangeEnd || end.toInstant(timeZone) <= rangeStart) continue

        recordCoveredDays(
            start = start,
            end = end,
            visibleDays = visibleDays,
            dotKey = dotKey,
            isAllDay = override.isAllDay,
            occurrenceSortId = OccurrenceId.Recurrence(row.eventId, override.recurrenceKey).value,
        )
    }
}

/**
 * Step 2 (final ordering): each day now holds one key per calendar+color pair, the earliest event carrying
 * that pair. Sort on it so the output mirrors planning's per-day event order.
 */
private fun DotOrderByDay.toDotColorsByDay(): Map<LocalDate, List<DotColor>> {
    return mapValues { (_, sortKeyByDotKey) ->
        sortKeyByDotKey.entries
            .sortedWith(
                compareBy<Map.Entry<DotKey, DayColorSortKey>>(
                    { it.value },
                    { it.key.calendarId.url },
                    { it.key.dotColor.sourceColor },
                ),
            ).map { (dotKey, _) -> dotKey.dotColor }
    }
}

private typealias DotOrderByDay = MutableMap<LocalDate, MutableMap<DotKey, DayColorSortKey>>

/** What a single dot stands for: one color *of one calendar*, so equal colors of two calendars are two dots. */
private data class DotKey(val calendarId: CalendarId, val dotColor: DotColor)

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
 * Record [dotKey] on every visible day the `[start, end]` span covers, keying the first day on the
 * event itself and the following ones on midnight.
 */
private fun DotOrderByDay.recordCoveredDays(
    start: LocalDateTime,
    end: LocalDateTime,
    visibleDays: ClosedRange<LocalDate>,
    dotKey: DotKey,
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

        val sortKeyByDotKey = getOrPut(day) { LinkedHashMap() }
        val previous = sortKeyByDotKey[dotKey]
        // Step 1 (per-calendar+color reduction): a day can contain multiple events sharing a dot.
        // Keep only the earliest event key for that dot (min sort key), because this key drives
        // the final per-day ordering once all events have been folded.
        if (previous == null || sortKey < previous) sortKeyByDotKey[dotKey] = sortKey

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
