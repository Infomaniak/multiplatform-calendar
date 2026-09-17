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

/**
 * Routes CalDAV traffic through an intercepting proxy, e.g. `http://127.0.0.1:9090`. The bridge's HTTP
 * stack ignores the OS proxy settings, hence the explicit URL.
 *
 * Shipped in every build of the library, so **the consumer must gate it**: a `debug` source set on
 * Android, `#if DEBUG` on Apple. Passing it widens the trust store and exposes credentials to the proxy.
 */
data class CaldavDebugInterception(
    /** Defaults to the host machine as seen from an emulator; override it for a physical device. */
    val proxyUrl: String = DEFAULT_DEBUG_PROXY_URL,
    /** Typically the proxy's root CA: the bridge's TLS store ignores user-installed certificates. */
    val extraRootCertificatesPem: List<String> = emptyList(),
)

/** Address of a proxy running on the developer's machine, as reachable from this platform. */
expect val DEFAULT_DEBUG_PROXY_URL: String

/** PEM is base64 text wrapped in ASCII armour, so the encoding is lossless. */
internal fun CaldavDebugInterception?.encodedExtraRootCertificates(): List<ByteArray> {
    return this?.extraRootCertificatesPem.orEmpty().map { it.encodeToByteArray() }
}
