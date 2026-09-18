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

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrence.RecurrenceScope

/**
 * What a mutation is aimed at, scope included: deletes and updates both take one of these.
 *
 * Built from the [Event] acted on ([Event.targetedAs]), never by hand, so a scope can only ever be
 * paired with an id that does name an occurrence.
 */
public sealed interface OccurrenceTarget {

    public val occurrenceId: OccurrenceId

    /** A plain event, or a series named by its master: there is no date picked, so nothing to scope. */
    @ConsistentCopyVisibility
    public data class Single internal constructor(override val occurrenceId: OccurrenceId) : OccurrenceTarget

    /** One occurrence of a series, reached as far along it as [scope] says. */
    @ConsistentCopyVisibility
    public data class Recurring internal constructor(
        override val occurrenceId: OccurrenceId,
        val scope: RecurrenceScope,
    ) : OccurrenceTarget
}

/**
 * This event as the target of a mutation reaching [scope] of its series.
 *
 * Only an occurrence can be scoped; anything else names a whole resource, and [scope] is dropped
 * rather than obeyed. The choices worth offering are in [Event.recurrenceScopes].
 */
public fun Event.targetedAs(scope: RecurrenceScope): OccurrenceTarget = when {
    isOccurrence -> OccurrenceTarget.Recurring(occurrenceId, scope)
    else -> OccurrenceTarget.Single(occurrenceId)
}

/** This event as the target of a mutation of the whole resource. */
public fun Event.targetedWhole(): OccurrenceTarget = OccurrenceTarget.Single(OccurrenceId.Master(occurrenceId.masterId))
