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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * One day's worth of an [Event], as seen on a planning grid.
 *
 * An event that spans several days (all-day or timed) is split into one [EventDaySlice] per day it
 * covers. A single-day event yields exactly one slice. This is the **only** consumer-facing type
 * for planning: recurrence (RRULE), when added later, will expand a master [Event] into synthetic
 * per-instance [Event]s *before* the day split, so the day-split contract below never changes.
 *
 * All fields are expressed in the `timeZone` passed to [expandDaySlices]:
 * - [displayStart] / [displayEnd] are always full `date + time`, clamped to this [date] (never null,
 *   which maps cleanly onto platforms with a single mandatory date-time type such as Apple `Date`).
 *   [displayStart] is `>= date 00:00`, [displayEnd] is `<= (date + 1) 00:00` and **exclusive**.
 * - [position] ([DaySpanPosition]) locates this day within the event's span and is **absolute to the
 *   event** (not to the visible window): a slice keeps the same index whether or not the window
 *   happens to include the event's real first/last day.
 *
 * The boolean flags callers usually want ([isFirstDay], [isLastDay], [isAllDay], [fillsWholeDay])
 * are derived properties, not stored, so they can never drift out of sync with the data.
 */
public data class EventDaySlice(
    val event: Event,
    val date: LocalDate,
    val displayStart: LocalDateTime,
    val displayEnd: LocalDateTime,
    val position: DaySpanPosition,
) {
    val isFirstDay: Boolean get() = position.isFirst
    val isLastDay: Boolean get() = position.isLast
    val isAllDay: Boolean get() = event.timing.isAllDay
    /**
     * Whether this slice covers the whole day (00:00 → next 00:00): true iCal all-day events and the
     * continuation days of a timed multi-day event. Use [isAllDay] to tell the two apart.
     */
    val fillsWholeDay: Boolean
        get() = displayStart.time == MIDNIGHT && displayEnd == LocalDateTime(date.plus(1, DateTimeUnit.DAY), MIDNIGHT)

    internal companion object {
        val MIDNIGHT = LocalTime(0, 0)
    }
}

/**
 * Expand every event into [EventDaySlice]s over the range `[rangeStart, rangeEnd[` (in [timeZone]),
 * then group them by day and sort each day for direct planning display.
 *
 * Within a day: all-day slices first, then timed slices by [EventDaySlice.displayStart], with
 * [EventId.url] as a stable tie-breaker. The map preserves ascending day order.
 *
 * The visible day span is derived from the range in [timeZone]; a [rangeEnd] landing exactly on
 * midnight is exclusive (it does not reveal the following day).
 */
internal fun List<Event>.groupDaySlicesByDay(
    rangeStart: Instant,
    rangeEnd: Instant,
    timeZone: TimeZone,
): Map<LocalDate, List<EventDaySlice>> {
    val fromDay = rangeStart.toLocalDateTime(timeZone).date
    val toDay = rangeEnd.toLocalDateTime(timeZone).lastInclusiveDay(notBefore = fromDay)
    val visibleDays = fromDay..toDay

    return flatMap { it.expandDaySlices(visibleDays, timeZone) }
        .sortedWith(daySliceComparator)
        .groupBy(EventDaySlice::date)
}

/**
 * Expand this event into one [EventDaySlice] per day it covers, restricted to [visibleDays]
 * (inclusive on both ends) and expressed in [timeZone].
 *
 * The event is first reprojected into [timeZone] via [EventTiming.startIn] / [EventTiming.endIn], so
 * a cross-zone "flight" (start zone ≠ end zone) and floating events are placed on the grid the user
 * is looking at. `dayIndex` / `dayCount` are computed against the event's own first/last day, then
 * only the days intersecting [visibleDays] are emitted (a long event seen through a small window
 * produces just the visible slices, but with their true absolute indices).
 *
 * The result is **unsorted** and side-effect free; ordering/grouping for display is the caller's job.
 */
internal fun Event.expandDaySlices(visibleDays: ClosedRange<LocalDate>, timeZone: TimeZone): List<EventDaySlice> {
    val startLocalDateTime = timing.startIn(targetZone = timeZone)
    val endLocalDateTime = timing.endIn(targetZone = timeZone)

    val firstDay = startLocalDateTime.date
    val lastDay = endLocalDateTime.lastInclusiveDay(notBefore = firstDay)
    val eventDayCount = firstDay.daysUntil(lastDay) + 1

    val from = maxOf(firstDay, visibleDays.start)
    val to = minOf(lastDay, visibleDays.endInclusive)
    if (from > to) return emptyList()

    return List(from.daysUntil(to) + 1) { index ->
        val day = from.plus(index, DateTimeUnit.DAY)
        EventDaySlice(
            event = this,
            date = day,
            displayStart = if (day == firstDay) startLocalDateTime else LocalDateTime(date = day, time = MIDNIGHT),
            displayEnd = if (day == lastDay) endLocalDateTime
            else LocalDateTime(date = day.plus(1, DateTimeUnit.DAY), time = MIDNIGHT),
            position = DaySpanPosition(index = firstDay.daysUntil(day), count = eventDayCount),
        )
    }
}

/** All-day slices first, then timed slices by start time; [EventId.url] breaks ties for stability. */
private val daySliceComparator = compareBy<EventDaySlice>(
    { it.date },
    { !it.isAllDay },
    { it.displayStart },
    { it.event.id.url },
)

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
private fun LocalDateTime.lastInclusiveDay(notBefore: LocalDate): LocalDate =
    if (time == MIDNIGHT && date > notBefore) date.minus(1) else maxOf(date, notBefore)

private fun LocalDate.minus(days: Int): LocalDate = plus(-days, DateTimeUnit.DAY)
