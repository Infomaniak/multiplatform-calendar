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
package com.infomaniak.multiplatform_calendar.core.extensions

import com.infomaniak.multiplatform_calendar.core.domain.model.event.MeetRoom

private const val MARKER = "/*----------------------------------------*/"
private const val MARKED_BLOCKS = "/\\*----------------------------------------\\*/"
private val MARKED_BLOCKS_REGEX = Regex("$MARKED_BLOCKS[\\s\\S]*?$MARKED_BLOCKS")
private val SPACED_MARKED_BLOCKS_REGEX = Regex("\\s*$MARKED_BLOCKS[\\s\\S]*?$MARKED_BLOCKS")

internal fun String.removeMarkedBlocks(): String {
    return this.replace(MARKED_BLOCKS_REGEX, "").trim()
}

/**
 * Replaces user-visible text while preserving the marked blocks from [original].
 *
 * If the user-visible text is unchanged, the original description is returned as-is.
 * Otherwise, the previous user-visible text is discarded, and the preserved blocks
 * are appended after the updated text.
 */
internal fun String?.withMarkedBlocksFrom(original: String?): String? {
    val text = this?.removeMarkedBlocks().orEmpty()

    if (text == original?.removeMarkedBlocks().orEmpty()) return original

    val blocks = original?.let { MARKED_BLOCKS_REGEX.findAll(it).iterator() }
    if (blocks == null || !blocks.hasNext()) return text.ifEmpty { null }

    return buildString {
        if (text.isNotEmpty()) {
            append(text)
            append("\n\n")
        }
        while (blocks.hasNext()) {
            append(blocks.next().value)
            if (blocks.hasNext()) append("\n\n")
        }
    }
}

internal fun markedBlock(text: String): String = "$MARKER\n$text\n\n$MARKER"

/** Swaps the marked block holding [previousUrl] for the one of [meetRoom], when the room changed. */
internal fun String?.withMeetRoomBlock(previousUrl: String?, meetRoom: MeetRoom?): String? {
    val previous = previousUrl?.ifBlank { null }
    if (meetRoom?.url == previous) return this

    val withoutPrevious = when (previous) {
        null -> this
        else -> this
            ?.replace(SPACED_MARKED_BLOCKS_REGEX) { block -> if (previous in block.value) "" else block.value }
            ?.trim()
    }
    val block = meetRoom?.descriptionBlock ?: return withoutPrevious?.ifEmpty { null }
    return if (withoutPrevious.isNullOrEmpty()) block else "$withoutPrevious\n\n$block"
}
