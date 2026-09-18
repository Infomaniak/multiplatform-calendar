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
package com.infomaniak.multiplatform_calendar.core.domain.model.event

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceKey
import kotlinx.serialization.Serializable

/** Identifier of a displayed event occurrence. */
@Serializable
public sealed class OccurrenceId {

    internal abstract val masterId: EventId

    /** [Master]: same value as [EventId.url]. [Recurrence]: tagged, see [OccurrenceId.parse]. */
    public abstract val value: String

    final override fun toString(): String = value

    /** The event resource itself: a non-recurring event, or the master of a series. */
    @Serializable
    public data class Master(override val masterId: EventId) : OccurrenceId() {
        override val value: String = masterId.url
    }

    /** One instance of [masterId], whether it comes from the rule or from a `RECURRENCE-ID` override. */
    @Serializable
    internal data class Recurrence(
        override val masterId: EventId,
        val recurrenceKey: RecurrenceKey,
    ) : OccurrenceId() {
        override val value: String = "$OCCURRENCE_PREFIX${recurrenceKey.canonical}$SEPARATOR${masterId.url}"
    }

    public companion object {
        /**
         * Read back a [value] this class handed out, so a client holding only the exported string can
         * name the occurrence again in a write call.
         *
         * An instance is announced by its prefix rather than guessed at, and its key comes first
         * because a key never holds a [SEPARATOR] while a URL may hold several. Reading the tail
         * instead would take `…/series.ics#AllDay:2026-06-16` — one whole resource — for an instance
         * of another. Anything unannounced is a [Master].
         */
        public fun parse(value: String): OccurrenceId {
            if (!value.startsWith(OCCURRENCE_PREFIX)) return Master(EventId(value))

            val tagged = value.removePrefix(OCCURRENCE_PREFIX)
            val url = tagged.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (url.isEmpty()) return Master(EventId(value))

            val key = runCatching { RecurrenceKey.parse(tagged.substringBefore(SEPARATOR)) }.getOrNull()
                ?: return Master(EventId(value))

            return Recurrence(EventId(url), key)
        }
    }
}

/** Kept out of the class so they stay implementation details of [OccurrenceId.Recurrence]. */
private const val SEPARATOR = '#'
private const val OCCURRENCE_PREFIX = "occurrence$SEPARATOR"
