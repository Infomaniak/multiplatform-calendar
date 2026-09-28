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
package com.infomaniak.multiplatform_calendar.core.domain.model.event.alarm

import com.infomaniak.multiplatform_calendar.core.domain.model.event.Event
import com.infomaniak.multiplatform_calendar.core.domain.model.event.EventTiming
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The alarms of these events going off in `[from, until]`, soonest first, capped at [limit].
 *
 * Only the [actions] asked for are projected. Since an action is part of what tells two alarms apart
 * (see [alarmKey]), filtering one out never shifts the identity of the ones kept.
 *
 * The two kinds of trigger come from two lists: a **relative** one fires per occurrence, so it is read
 * from [occurrences], while an **absolute** one names a fixed instant that RFC 5545 §3.8.6.3 fires once
 * for the whole series, so it is read from [storedRows] — the masters and overrides as stored — and
 * rings whether or not an occurrence stands in the window. A non-recurring event is in both lists, but
 * each trigger is read from one only.
 */
internal fun upcomingAlarms(
    occurrences: List<Event>,
    storedRows: List<Event>,
    from: Instant,
    until: Instant,
    limit: Int,
    actions: Set<AlarmAction>,
    defaultZone: TimeZone,
): List<UpcomingAlarm> {
    val projected = ArrayList<UpcomingAlarm>()
    occurrences.projectInto(projected, from, until, actions, defaultZone) { it is AlarmTrigger.Relative }
    storedRows.projectInto(projected, from, until, actions, defaultZone) { it is AlarmTrigger.Absolute }

    return projected
        // Collapses the copies an absolute trigger cloned onto the overrides of its series, see [alarmKey].
        .distinctBy(UpcomingAlarm::id)
        .sortedBy(UpcomingAlarm::firesAt)
        .take(limit)
}

private fun List<Event>.projectInto(
    projected: MutableList<UpcomingAlarm>,
    from: Instant,
    until: Instant,
    actions: Set<AlarmAction>,
    defaultZone: TimeZone,
    triggers: (AlarmTrigger) -> Boolean,
) {
    for (event in this) {
        // Ordinals count within one event, per key, so that reordering an event's alarm list permutes
        // the ordinals of alarms already indistinguishable instead of renaming unrelated ones.
        // Filtering first is safe: alarms sharing a key fire at the very same instant, so no window and
        // no set of actions can ever keep one of a group and drop another.
        val ordinals = HashMap<String, Int>()
        for (alarm in event.alarms) {
            if (!triggers(alarm.trigger) || alarm.action !in actions) continue
            val firesAt = alarm.firesAt(event.timing, defaultZone)
            if (firesAt < from || firesAt > until) continue

            val key = alarm.alarmKey(event, firesAt)
            val ordinal = ordinals.getOrElse(key) { 0 }
            ordinals[key] = ordinal + 1

            projected += UpcomingAlarm(
                id = UpcomingAlarmId("$key|$ordinal"),
                firesAt = firesAt,
                alarm = alarm,
                event = event,
            )
        }
    }
}

/** When this alarm goes off, for an event happening at [timing]. */
private fun EventAlarm.firesAt(timing: EventTiming, defaultZone: TimeZone): Instant = when (val trigger = trigger) {
    is AlarmTrigger.Absolute -> trigger.instant
    is AlarmTrigger.Relative -> timing.startInstant(defaultZone) + trigger.offsetFromStart(timing, defaultZone)
}

/**
 * How long after the start of [timing] this trigger goes off. A `RELATED=END` one is counted from the
 * start too, by adding the event's duration, so that one anchor answers for both forms — as
 * `AlarmBoundsEntity` records per row.
 */
internal fun AlarmTrigger.Relative.offsetFromStart(timing: EventTiming, defaultZone: TimeZone): Duration {
    return when (relatedTo) {
        TriggerRelation.Start -> offset
        TriggerRelation.End -> (timing.endInstant(defaultZone) - timing.startInstant(defaultZone)) + offset
    }
}

/**
 * What tells this firing apart from the others: where it belongs, when it goes off, and which alarm
 * of that event set it off.
 *
 * An absolute trigger names one fixed point in time, which RFC 5545 §3.8.6.3 fires once for the whole
 * series: keying it on the series is what collapses the copies it was cloned onto the overrides of that
 * series into a single firing.
 */
private fun EventAlarm.alarmKey(event: Event, firesAt: Instant): String {
    val scope = when (trigger) {
        is AlarmTrigger.Absolute -> event.masterEventId.url
        is AlarmTrigger.Relative -> event.occurrenceId.value
    }
    return "$scope|$firesAt|${id.value}"
}
