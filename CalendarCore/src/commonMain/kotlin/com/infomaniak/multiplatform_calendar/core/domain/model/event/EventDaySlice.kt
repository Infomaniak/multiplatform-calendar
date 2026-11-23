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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventDaySlice.Companion.MIDNIGHT
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The days `[date, endDate]` of an event ([EventSummary]), as seen on a planning grid.
 *
 * An event that spans several days (all-day or timed) is split into one [EventDaySlice] per day it
 * covers, unless the [DaySliceGrouping] keeps them together: one slice then spans `[date, endDate]`.
 * A single-day event yields exactly one slice. This is the **only** consumer-facing type
 * for planning: a recurring event is expanded into its occurrences *before* the day split.
 *
 * All fields are expressed in the `timeZone` passed to [expandDaySlicesInto]:
 * - [displayStart] / [displayEnd] are always full `date + time`, clamped to `[date, endDate]` (never null,
 *   which maps cleanly onto platforms with a single mandatory date-time type such as Apple `Date`).
 *   [displayStart] is `>= date 00:00`, [displayEnd] is `<= (endDate + 1) 00:00` and **exclusive**.
 * - [position] ([DaySpanPosition]) locates [date] within the event's span and is **absolute to the
 *   event** (not to the visible window): a slice keeps the same index whether or not the window
 *   happens to include the event's real first/last day.
 *
 * The boolean flags callers usually want ([isFirstDay], [isLastDay], [isAllDay], [fillsWholeDay])
 * are derived properties, not stored, so they can never drift out of sync with the data.
 */
public data class EventDaySlice(
    val event: EventSummary,
    val date: LocalDate,
    val endDate: LocalDate,
    val displayStart: LocalDateTime,
    val displayEnd: LocalDateTime,
    val position: DaySpanPosition,
) {
    val dayCount: Int get() = date.daysUntil(endDate) + 1
    val isFirstDay: Boolean get() = position.isFirst
    val isLastDay: Boolean get() = position.index + dayCount == position.count
    val isAllDay: Boolean get() = event.timing.isAllDay
    /**
     * Whether this slice covers its whole days (00:00 → 00:00): true iCal all-day events and the
     * continuation days of a timed multi-day event. Use [isAllDay] to tell the two apart.
     */
    val fillsWholeDay: Boolean
        get() = displayStart.time == MIDNIGHT && displayEnd == LocalDateTime(endDate.plus(1, DateTimeUnit.DAY), MIDNIGHT)

    /**
     * Convert the slice's [displayStart] into an absolute [Instant] in the [timeZone] provided
     * or current system time zone if not provided.
     *
     * This is a convenience for display purposes; it does not reflect the event's original time zone
     * (which may be different, or null for floating events).
     */
    public fun displayStartInstant(timeZone: TimeZone? = null): Instant =
        displayStart.toInstant(timeZone ?: TimeZone.currentSystemDefault())

    /**
     * Convert the slice's [displayEnd] into an absolute [Instant] in the [timeZone] provided
     * or current system time zone if not provided.
     *
     * This is a convenience for display purposes; it does not reflect the event's original time zone
     * (which may be different, or null for floating events).
     */
    public fun displayEndInstant(timeZone: TimeZone? = null): Instant =
        displayEnd.toInstant(timeZone ?: TimeZone.currentSystemDefault())

    internal companion object {
        val MIDNIGHT = LocalTime(0, 0)
    }
}

/**
 * Expand every event into [EventDaySlice]s over the range `[rangeStart, rangeEnd[` (in [timeZone]),
 * then group them by day and sort each day for direct planning display.
 *
 * Within a day: slices spanning more days first (longest first), then all-day slices, then timed slices
 * by [EventDaySlice.displayStart], with [OccurrenceId.value] as a stable tie-breaker. The map preserves
 * ascending day order; a slice spanning several days ([grouping]) is only under its first day.
 *
 * The visible day span is derived from the range in [timeZone]; a [rangeEnd] landing exactly on
 * midnight is exclusive (it does not reveal the following day).
 *
 * Slices are dispatched directly into per-day buckets (an array indexed by day offset from [fromDay])
 * during expansion and each bucket is sorted independently, avoiding a flat list + global sort +
 * group-by, and any intermediate per-event buffer.
 *
 * CPU-bound `suspend` function: cancellation is checked before each event so a caller using
 * `mapLatest` (see `EventRepository.observeVisibleDaySlices`) can abandon a stale grouping.
 */
internal suspend fun List<EventSummary>.groupDaySlicesByDay(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
    grouping: DaySliceGrouping,
): Map<LocalDate, List<EventDaySlice>> {
    val fromDay = rangeStart.toLocalDateTime(timeZone).date
    val toDay = rangeEnd.toLocalDateTime(timeZone).lastInclusiveDay(notBefore = fromDay)
    val visibleDays = fromDay..toDay
    val dayCount = visibleDays.count()

    val buckets = arrayOfNulls<MutableList<EventDaySlice>>(dayCount)

    for (event in this@groupDaySlicesByDay) {
        currentCoroutineContext().ensureActive()
        event.expandDaySlicesInto(buckets, visibleDays, timeZone, grouping)
    }

    return buildMap(capacity = dayCount) {
        for (offset in 0 until dayCount) {
            currentCoroutineContext().ensureActive()
            val bucket = buckets[offset] ?: continue
            bucket.sortWith(perDayDaySliceComparator)
            put(fromDay.plus(offset, DateTimeUnit.DAY), bucket)
        }
    }
}

/**
 * Expand this event into [EventDaySlice]s over the days it covers within [visibleDays] (inclusive),
 * expressed in [timeZone], writing each slice straight into the [buckets] entry of its first day.
 * Without [grouping] each day gets its own slice; otherwise one slice spans the visible days, split
 * at each `weekStart` for [DaySliceGrouping.MultiDay].
 *
 * The event is reprojected into [timeZone] via [EventTiming.startIn] / [EventTiming.endIn] so
 * cross-zone "flights" and floating events land on the grid the user sees; `dayIndex` / `dayCount`
 * stay absolute to the event's own span, even when clamped to a smaller window.
 *
 * [buckets] is a caller-owned array indexed by day offset from `visibleDays.start` (the first visible
 * day); each slice is appended to `buckets[visibleDays.start.daysUntil(slice.date)]`, the bucket being
 * lazily created on first use. Writing directly into the shared per-day buckets lets
 * [groupDaySlicesByDay] expand every event with no intermediate per-event buffer and no redispatch
 * pass. The slices within a bucket are **unsorted**; cancellation is checked before each slice so a long
 * expansion stops promptly when the caller's `mapLatest` abandons it.
 */
internal suspend fun EventSummary.expandDaySlicesInto(
    buckets: Array<MutableList<EventDaySlice>?>,
    visibleDays: ClosedRange<LocalDate>,
    timeZone: TimeZone,
    grouping: DaySliceGrouping,
) {
    val startLocalDateTime = timing.startIn(targetZone = timeZone)
    val endLocalDateTime = timing.endIn(targetZone = timeZone)

    val firstDay = startLocalDateTime.date
    val lastDay = endLocalDateTime.lastInclusiveDay(notBefore = firstDay)
    val eventDayCount = firstDay.daysUntil(lastDay) + 1

    val from = maxOf(firstDay, visibleDays.start)
    val to = minOf(lastDay, visibleDays.endInclusive)
    if (from > to) return

    var day = from
    while (day <= to) {
        currentCoroutineContext().ensureActive()
        val endDay = lastGroupedDay(day, to, grouping)
        val offset = visibleDays.start.daysUntil(day)
        val bucket = buckets[offset] ?: mutableListOf<EventDaySlice>().also { buckets[offset] = it }
        bucket += EventDaySlice(
            event = this,
            date = day,
            endDate = endDay,
            displayStart = if (day == firstDay) startLocalDateTime else LocalDateTime(date = day, time = MIDNIGHT),
            displayEnd = if (endDay == lastDay) endLocalDateTime
            else LocalDateTime(date = endDay.plus(1, DateTimeUnit.DAY), time = MIDNIGHT),
            position = DaySpanPosition(index = firstDay.daysUntil(day), count = eventDayCount),
        )
        day = endDay.plus(1, DateTimeUnit.DAY)
    }
}

private fun EventSummary.lastGroupedDay(day: LocalDate, to: LocalDate, grouping: DaySliceGrouping): LocalDate =
    when (grouping) {
        DaySliceGrouping.None -> day
        DaySliceGrouping.AllDay -> if (timing.isAllDay) to else day
        is DaySliceGrouping.MultiDay -> {
            val daysSinceWeekStart = (day.dayOfWeek.ordinal - grouping.weekStart.ordinal).mod(DAYS_PER_WEEK)
            minOf(to, day.plus(DAYS_PER_WEEK - 1 - daysSinceWeekStart, DateTimeUnit.DAY))
        }
    }

private const val DAYS_PER_WEEK = 7

/**
 * Sorts slices within a single day: longest span first, then all-day, then timed by start time;
 * [OccurrenceId.value] breaks ties for stability. No `date` key — buckets are already mono-date.
 */
private val perDayDaySliceComparator = Comparator<EventDaySlice> { left, right ->
    right.dayCount.compareTo(left.dayCount).takeUnless { it == 0 } ?: comparePerDayDisplayOrder(
        leftIsAllDay = left.isAllDay,
        leftDisplayStart = left.displayStart,
        leftOccurrenceSortId = left.event.occurrenceId.value,
        rightIsAllDay = right.isAllDay,
        rightDisplayStart = right.displayStart,
        rightOccurrenceSortId = right.event.occurrenceId.value,
    )
}

/**
 * Convert an exclusive end date-time into the last day it actually covers.
 *
 * iCal's DTEND is exclusive: an end landing exactly on midnight belongs to the previous day, so a
 * timed event finishing at 00:00 (or an all-day event) doesn't spill a phantom slice onto the next
 * day. The result is clamped to [notBefore] to keep single-instant or zero-length spans valid
 * (e.g. an event that starts and ends on the same day still yields that day).
 *
 * Examples (start day = Mon):
 * - Mon 10:00 → Wed 00:00  ⇒ Tue (midnight on a later day = previous day)
 * - Mon 00:00 → Mon 00:00  ⇒ Mon (clamped to notBefore, no negative span)
 * - Mon 10:00 → Tue 14:00  ⇒ Tue (non-midnight end day kept as-is)
 */
internal fun LocalDateTime.lastInclusiveDay(notBefore: LocalDate): LocalDate =
    if (time == MIDNIGHT && date > notBefore) date.minus(1) else maxOf(date, notBefore)

private fun LocalDate.minus(days: Int): LocalDate = plus(-days, DateTimeUnit.DAY)
