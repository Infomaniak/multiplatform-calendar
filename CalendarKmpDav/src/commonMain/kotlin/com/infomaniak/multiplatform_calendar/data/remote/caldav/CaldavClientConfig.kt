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
import uniffi.caldav_bridge.DavClientConfig as RustDavClientConfig
import uniffi.caldav_bridge.configureDavClient as rustConfigureDavClient
import uniffi.caldav_bridge.evictDavClients as rustEvictDavClients

/**
 * Tunables applied to every CalDAV connection opened by the bridge, where `null` keeps the
 * underlying library's own default. Install with [configureCaldavClient].
 */
data class CaldavClientConfig(
    val userAgent: String? = null,
    val requestTimeout: Duration? = null,
    val connectTimeout: Duration? = null,
    val maxIdleConnectionsPerHost: Int? = null,
    val idleConnectionTimeout: Duration? = null,
    val debugInterception: CaldavDebugInterception? = null,
) {
    init {
        // These cross into Rust as unsigned, where a negative value would silently wrap into a huge
        // one. The bridge rejects zero too, so reject anything non-positive here for a clear error.
        requirePositive(requestTimeout?.inWholeMilliseconds, "requestTimeout")
        requirePositive(connectTimeout?.inWholeMilliseconds, "connectTimeout")
        requirePositive(maxIdleConnectionsPerHost?.toLong(), "maxIdleConnectionsPerHost")
        requirePositive(idleConnectionTimeout?.inWholeMilliseconds, "idleConnectionTimeout")
    }
}

/** Also rejects zero, which is what a sub-millisecond [Duration] truncates to. */
private fun requirePositive(value: Long?, name: String) {
    require(value == null || value > 0L) { "$name must be > 0, was $value" }
}

/**
 * Routes CalDAV traffic through an intercepting proxy, e.g. `http://127.0.0.1:9090`. Honoured only
 * when the native library is built with the `debug-interception` Cargo feature, enabled for Android
 * debug variants; a release build silently ignores these settings.
 */
data class CaldavDebugInterception(
    val proxyUrl: String,
    /** Typically the proxy's root CA: Android's system trust store ignores user-installed ones. */
    val extraRootCertificatesPem: List<String> = emptyList(),
)

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
    timeoutMs = requestTimeout?.inWholeMilliseconds?.toULong(),
    connectTimeoutMs = connectTimeout?.inWholeMilliseconds?.toULong(),
    poolMaxIdlePerHost = maxIdleConnectionsPerHost?.toUInt(),
    poolIdleTimeoutMs = idleConnectionTimeout?.inWholeMilliseconds?.toULong(),
    proxyUrl = debugInterception?.proxyUrl,
    // PEM is base64 text wrapped in ASCII armour, so the encoding is lossless.
    extraRootCertsPem = debugInterception?.extraRootCertificatesPem.orEmpty().map { it.encodeToByteArray() },
)
