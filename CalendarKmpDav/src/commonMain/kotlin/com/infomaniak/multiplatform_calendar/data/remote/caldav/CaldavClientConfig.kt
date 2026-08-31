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
package com.infomaniak.multiplatform_calendar.data.remote.caldav

import com.infomaniak.multiplatform_calendar.data.remote.caldav.model.DavAccount
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import uniffi.caldav_bridge.DavClientConfig as RustDavClientConfig
import uniffi.caldav_bridge.configureDavClient as rustConfigureDavClient
import uniffi.caldav_bridge.evictDavClients as rustEvictDavClients

/**
 * Tunables applied to every CalDAV connection opened by the bridge. Install with
 * [configureCaldavClient].
 *
 * Every knob but [userAgent] has a default, pinned here rather than left to the underlying library so
 * the effective behaviour stays explicit and reproducible across `fast-dav-rs` upgrades.
 */
data class CaldavClientConfig(
    val userAgent: String,
    val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val maxIdleConnections: Int = DEFAULT_MAX_IDLE_CONNECTIONS,
    val keepAliveDuration: Duration = DEFAULT_KEEP_ALIVE_DURATION,
    val debugInterception: CaldavDebugInterception? = null,
) {
    init {
        require(userAgent.isNotBlank()) { "userAgent must not be blank" }
        // These cross into Rust as unsigned, where a negative value would silently wrap into a huge
        // one. The bridge rejects zero too, so reject anything non-positive here for a clear error.
        requirePositive(requestTimeout.inWholeMilliseconds, "requestTimeout")
        requirePositive(connectTimeout.inWholeMilliseconds, "connectTimeout")
        requirePositive(maxIdleConnections.toLong(), "maxIdleConnections")
        requirePositive(keepAliveDuration.inWholeMilliseconds, "keepAliveDuration")
    }

    companion object {
        /** The `fast-dav-rs` default. */
        val DEFAULT_REQUEST_TIMEOUT: Duration = 20.seconds
        /** `reqwest` has none and defers to the OS, whose SYN retry budget runs into minutes. */
        val DEFAULT_CONNECT_TIMEOUT: Duration = 10.seconds
        /** The `fast-dav-rs` default. */
        const val DEFAULT_MAX_IDLE_CONNECTIONS: Int = 32
        /** The `hyper` default. */
        val DEFAULT_KEEP_ALIVE_DURATION: Duration = 90.seconds
    }
}

/** Also rejects zero, which is what a sub-millisecond [Duration] truncates to. */
private fun requirePositive(value: Long, name: String) {
    require(value > 0L) { "$name must be > 0, was $value" }
}

/** Call once at startup, before the first sync. Calling it again drops the existing connections. */
fun configureCaldavClient(config: CaldavClientConfig) = rustConfigureDavClient(config.toRust())

/**
 * Drop the connections cached for [account], along with the password they were built with.
 *
 * Call whenever credentials are removed or replaced. The cache is keyed by password, so without this
 * a rotation leaves the previous client resident for the lifetime of the process.
 */
fun evictCaldavClient(account: DavAccount) = rustEvictDavClients(account.baseUrl, account.username)

private fun CaldavClientConfig.toRust() = RustDavClientConfig(
    userAgent = userAgent,
    timeoutMs = requestTimeout.inWholeMilliseconds.toULong(),
    connectTimeoutMs = connectTimeout.inWholeMilliseconds.toULong(),
    poolMaxIdlePerHost = maxIdleConnections.toUInt(),
    poolIdleTimeoutMs = keepAliveDuration.inWholeMilliseconds.toULong(),
    proxyUrl = debugInterception?.proxyUrl,
    extraRootCertsPem = debugInterception.encodedExtraRootCertificates(),
)
