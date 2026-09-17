//! Internal helpers to build the CalDAV client.
//!
//! These are crate-private building blocks shared by the calendar and event
//! operation modules.

use fast_dav_rs::CalDavClient;
use http::Response;

use crate::config::cached_client;
use crate::error::{CaldavError, http_status_error};
use crate::models::DavAccount;

/// Build a [`CalDavClient`] authenticated with [`account`].
///
/// Cached per account (see [`crate::config`]), so repeated operations reuse the same TLS setup and
/// connection pool.
pub(crate) fn client(account: &DavAccount) -> Result<CalDavClient, CaldavError> {
    cached_client(account)
}

/// Fail unless the CalDAV response carries a 2xx status.
///
/// Some low-level `fast-dav-rs` methods return the HTTP response regardless
/// of its status instead of returning [`fast_dav_rs::Error::UnexpectedStatus`].
///
/// Convert these responses to the same [`CaldavError::RustHttpException`] domain
/// used by [`map_fast_dav_error`] so callers get consistent HTTP error
/// handling regardless of which `fast-dav-rs` API produced the response.
pub(crate) fn ensure_success<T>(context: &str, resp: &Response<T>) -> Result<(), CaldavError> {
    if resp.status().is_success() {
        Ok(())
    } else {
        Err(http_status_error(context, resp.status()))
    }
}
