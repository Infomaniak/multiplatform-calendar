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
package com.infomaniak.multiplatform_calendar.di

import com.infomaniak.multiplatform_calendar.data.remote.caldav.CaldavClientConfig
import com.infomaniak.multiplatform_calendar.data.remote.caldav.CaldavDebugInterception
import com.infomaniak.multiplatform_calendar.data.remote.caldav.DEFAULT_DEBUG_PROXY_URL
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Tunables applied to every CalDAV connection, as seen by Apple consumers. Pass one to
 * [CalendarSDKProvider.sdk].
 *
 * Core-owned counterpart of the internal `:kmpdav` [CaldavClientConfig], for the same reason as
 * [com.infomaniak.multiplatform_calendar.core.domain.model.account.DavCredentials]: the bridge module
 * must never be exported into the public Apple framework. One deliberate difference: timeouts are
 * plain seconds rather than `Duration`, because a Kotlin `value class` exported as a *property* is
 * boxed to `Any`, which makes it unusable from Swift (same reason as the id accessors in
 * `AppleIdAccessors`).
 */
public data class CaldavClientSettings(
    val userAgent: String,
    val requestTimeoutSeconds: Double = CaldavClientConfig.DEFAULT_REQUEST_TIMEOUT.inSeconds,
    val connectTimeoutSeconds: Double = CaldavClientConfig.DEFAULT_CONNECT_TIMEOUT.inSeconds,
    val maxIdleConnections: Int = CaldavClientConfig.DEFAULT_MAX_IDLE_CONNECTIONS,
    val keepAliveDurationSeconds: Double = CaldavClientConfig.DEFAULT_KEEP_ALIVE_DURATION.inSeconds,
    val debugInterception: CaldavDebugInterceptionSettings? = null,
)

/**
 * Routes CalDAV traffic through an intercepting proxy, e.g. `http://127.0.0.1:9090`.
 *
 * Nothing in the framework gates this, so build it from `#if DEBUG` only: it widens the trust store
 * and exposes credentials to the proxy.
 */
public data class CaldavDebugInterceptionSettings(
    val proxyUrl: String = DEFAULT_DEBUG_PROXY_URL,
    val extraRootCertificatesPem: List<String> = emptyList(),
)

internal fun CaldavClientSettings.toBridgeConfig() = CaldavClientConfig(
    userAgent = userAgent,
    requestTimeout = requestTimeoutSeconds.seconds,
    connectTimeout = connectTimeoutSeconds.seconds,
    maxIdleConnections = maxIdleConnections,
    keepAliveDuration = keepAliveDurationSeconds.seconds,
    debugInterception = debugInterception?.let {
        CaldavDebugInterception(proxyUrl = it.proxyUrl, extraRootCertificatesPem = it.extraRootCertificatesPem)
    },
)

private val Duration.inSeconds: Double get() = toDouble(DurationUnit.SECONDS)
