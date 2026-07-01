//! Error type exposed to Kotlin via UniFFI.

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CaldavError {
    #[error("{msg}")]
    Bridge { msg: String },

    #[error("{msg}")]
    RustNetworkException { msg: String },
}

/// Build a [`CaldavError::Bridge`] with a context-prefixed message.
pub(crate) fn bridge_error(context: &str, e: impl std::fmt::Display) -> CaldavError {
    CaldavError::Bridge { msg: format!("{context}: {e}") }
}

/// Build a [`CaldavError::RustNetworkException`] with a context-prefixed message.
pub(crate) fn rust_network_error(context: &str, e: impl std::fmt::Display) -> CaldavError {
    CaldavError::RustNetworkException { msg: format!("{context}: {e}") }
}

/// Map an error to either `RustNetworkException` (connectivity) or generic `Bridge`.
pub(crate) fn network_or_bridge_error(
    context: &str,
    e: impl std::fmt::Display,
) -> CaldavError {
    let message = e.to_string();
    if has_network_keyword(&message) {
        rust_network_error(context, message)
    } else {
        bridge_error(context, message)
    }
}

fn has_network_keyword(message: &str) -> bool {
    let lower = message.to_ascii_lowercase();

    // fast-dav-rs (hyper stack) typically formats transport failures as
    // "client error (Connect)".
    if lower.contains("client error (connect)") {
        return true;
    }

    [
        "dns",
        "timeout",
        "timed out",
        "request timed out",
        "connect)",
        "connection refused",
        "connection reset",
        "connection aborted",
        "network is unreachable",
        "host is unreachable",
        "not connected",
        "name resolution",
        "failed to lookup",
        "temporary failure",
        "unreachable",
    ]
    .iter()
    .any(|keyword| lower.contains(keyword))
}
