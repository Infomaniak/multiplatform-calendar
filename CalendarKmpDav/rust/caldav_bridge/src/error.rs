//! Error type exposed to Kotlin via UniFFI.

use std::io;

use fast_dav_rs::Error as FastDavError;
use http::StatusCode;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CaldavError {
    #[error("{msg}")]
    Bridge { msg: String },

    #[error("{msg}")]
    RustNetworkException { msg: String },

    #[error("{msg}")]
    RustHttpException { status_code: u16, operation: String, msg: String },
}

/// Build a [`CaldavError::Bridge`] with a context-prefixed message.
pub(crate) fn bridge_error(context: &str, error: impl std::fmt::Display) -> CaldavError {
    CaldavError::Bridge {
        msg: format!("{context}: {error}"),
    }
}

/// Build a [`CaldavError::RustNetworkException`] with a context-prefixed message.
fn rust_network_error(context: &str, error: impl std::fmt::Display) -> CaldavError {
    CaldavError::RustNetworkException {
        msg: format!("{context}: {error}"),
    }
}

/// Build a [`CaldavError::RustHttpException`] from an HTTP response status.
///
/// This is also used by [`map_fast_dav_error`] for
/// [`FastDavError::UnexpectedStatus`] so both:
///
/// - high-level `fast-dav-rs` operations returning `UnexpectedStatus`, and
/// - low-level operations returning an HTTP response checked by `ensure_success`
///
/// expose the same error representation through UniFFI.
pub(crate) fn http_status_error(operation: impl Into<String>, status: StatusCode) -> CaldavError {
    let operation = operation.into();
    let status_code = status.as_u16();

    CaldavError::RustHttpException {
        status_code,
        msg: format!("{operation} failed with HTTP {status}"),
        operation,
    }
}

/// Maps a typed `fast-dav-rs` error to the stable error domain exposed through UniFFI.
///
/// Keep `fast-dav-rs::Error` internal to the Rust bridge so changes in the DAV
/// implementation don't leak into the public Kotlin/Swift API.
pub(crate) fn map_fast_dav_error(context: &str, error: FastDavError) -> CaldavError {
    match error {
        FastDavError::UnexpectedStatus { operation, status, .. } => {
            http_status_error(operation.to_string(), status)
        }
        error if is_network_error(&error) => {
            rust_network_error(context, error)
        }
        error => bridge_error(context, error),
    }
}

/// Whether the DAV failure belongs to the network/transport domain.
///
/// `fast-dav-rs >= 0.8` already classifies Hyper client failures into
/// `Connection` and `Transport`, so we don't need to walk `Error::source()`
/// or downcast Hyper internals anymore.
fn is_network_error(error: &FastDavError) -> bool {
    match error {
        FastDavError::Connection(_)
        | FastDavError::Transport(_)
        | FastDavError::Hyper(_)
        | FastDavError::Timeout { .. } => true,

        // Defensive fallback for network-related std::io errors which might
        // surface directly rather than through Connection/Transport.
        FastDavError::Io(error) => is_network_io_kind(error.kind()),

        // `Error` is #[non_exhaustive], so this wildcard is intentionally
        // required and makes us forward-compatible with future variants.
        _ => false,
    }
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
            | io::ErrorKind::AddrNotAvailable
    )
}
