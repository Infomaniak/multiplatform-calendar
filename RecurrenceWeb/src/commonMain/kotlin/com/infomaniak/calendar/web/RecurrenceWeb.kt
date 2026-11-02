package com.infomaniak.calendar.web

import com.infomaniak.calendar.web.resources.Res
import com.infomaniak.calendar.web.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.js.JsExport

/**
 * Web bridge. Calls back after Compose Resources has loaded the localized strings.
 * The resource locale comes from the browser's system locale, not from a second
 * handwritten translation table; [locale] is retained for API symmetry and diagnostics.
 * This is an RFC 5545 subset demonstration, not CalendarCore's production formatter.
 */
@JsExport
fun formatRRule(raw: String, locale: String, success: (String) -> Unit, failure: (String) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            success(formatLocalized(raw))
        } catch (e: Throwable) {
            failure(e.message ?: "Unknown formatting error")
        }
    }
}

private suspend fun formatLocalized(raw: String): String {
    val input = raw.trim().removePrefix("RRULE:")
    val parts = input.split(';')
    val fields = parts.associate { part ->
        val pair = part.split('=', limit = 2)
        require(pair.size == 2 && pair[0].isNotBlank() && pair[1].isNotBlank()) { "Invalid field: $part" }
        pair[0].uppercase() to pair[1].uppercase()
    }
    require(fields.size == parts.size) { "Duplicate RRULE field" }
    val unsupported = fields.keys - setOf("FREQ", "INTERVAL", "BYDAY", "COUNT")
    require(unsupported.isEmpty()) { "Unsupported fields: ${unsupported.joinToString()}" }
    val frequency = fields["FREQ"] ?: error("FREQ is required")
    val index = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").indexOf(frequency)
    require(index >= 0) { "Unsupported FREQ: $frequency" }
    val interval = fields["INTERVAL"]?.toIntOrNull() ?: if ("INTERVAL" in fields) error("Invalid INTERVAL") else 1
    require(interval > 0) { "INTERVAL must be positive" }
    val count = fields["COUNT"]?.toIntOrNull()
    require("COUNT" !in fields || (count != null && count > 0)) { "COUNT must be positive" }
    val days = fields["BYDAY"]?.split(',').orEmpty()
    val codes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    require(days.all { it in codes }) { "Invalid BYDAY" }
    require(days.isEmpty() || frequency == "WEEKLY") { "BYDAY is supported only for WEEKLY in this demo" }

    val every = listOf(Res.string.every_day, Res.string.every_week, Res.string.every_month, Res.string.every_year)
    val plural = listOf(Res.string.plural_day, Res.string.plural_week, Res.string.plural_month, Res.string.plural_year)
    var result = if (interval == 1) getString(every[index])
        else getString(Res.string.every_interval, interval, getString(plural[index]))
    if (days.isNotEmpty()) {
        val names: List<StringResource> = listOf(
            Res.string.day_mo, Res.string.day_tu, Res.string.day_we, Res.string.day_th,
            Res.string.day_fr, Res.string.day_sa, Res.string.day_su
        )
        val readable = days.map { getString(names[codes.indexOf(it)]) }.joinToString(", ")
        result += getString(Res.string.days_clause, readable)
    }
    if (count != null) result += getString(Res.string.count_clause, count)
    return result
}

fun main() = Unit
