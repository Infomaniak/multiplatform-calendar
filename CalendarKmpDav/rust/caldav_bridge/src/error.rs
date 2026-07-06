//! Error type exposed to Kotlin via UniFFI.

use std::error::Error;
use std::io;

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

/// Map an error to either [`CaldavError::RustNetworkException`] (connectivity failure) or a
/// generic [`CaldavError::Bridge`].
///
/// Connectivity detection walks the [`Error::source`] chain and downcasts each cause to
/// [`io::Error`], matching against a fixed set of [`io::ErrorKind`] variants. This is robust
/// against upstream wording/locale changes and does not rely on string sniffing.
///
/// Callers typically obtain the reference from an `anyhow::Error` via `err.as_ref()`.
pub(crate) fn network_or_bridge_error(context: &str, err: &(dyn Error + 'static)) -> CaldavError {
    if is_network_error(err) {
        rust_network_error(context, err)
    } else {
        bridge_error(context, err)
    }
}

fn is_network_error(err: &(dyn Error + 'static)) -> bool {
    let mut current: Option<&(dyn Error + 'static)> = Some(err);
    while let Some(cause) = current {
        // hyper-util's connect failures (DNS, TCP connect) are the primary signal from the
        // fast-dav-rs stack. Its Display is "client error (Connect)"
        if let Some(hyper_err) = cause.downcast_ref::<hyper_util::client::legacy::Error>() {
            if hyper_err.is_connect() {
                return true;
            }
        }
        // Fallback: any io::Error deeper in the chain with a well-known connectivity kind.
        // Note: `getaddrinfo` failures currently surface as `Uncategorized`, which is why the
        // hyper-util check above is the main path for DNS/Connect errors on Android.
        if let Some(io_err) = cause.downcast_ref::<io::Error>() {
            if is_network_io_kind(io_err.kind()) {
                return true;
            }
        }
        current = cause.source();
    }
    false
}

fn is_network_io_kind(kind: io::ErrorKind) -> bool {
    matches!(
        kind,
        io::ErrorKind::NotConnected
            | io::ErrorKind::ConnectionRefused
            | io::ErrorKind::ConnectionReset
            | io::ErrorKind::ConnectionAborted
            | io::ErrorKind::NetworkUnreachable
            | io::ErrorKind::HostUnreachable
            | io::ErrorKind::TimedOut
            | io::ErrorKind::AddrNotAvailable,
    )
}
