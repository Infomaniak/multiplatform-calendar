//! Internal helpers to build the Tokio runtime and the CalDAV client.
//!
//! These are crate-private building blocks shared by the calendar and event
//! operation modules.

use fast_dav_rs::CalDavClient;
use http::Response;
use tokio::runtime::Runtime;

use crate::error::{bridge_error, CaldavError};
use crate::models::DavAccount;

/// Create a multi-thread Tokio runtime used to drive the async CalDAV calls.
pub(crate) fn rt() -> Result<Runtime, CaldavError> {
    Runtime::new().map_err(|e| bridge_error("Tokio", e))
}

/// Build a [`CalDavClient`] authenticated with [`account`].
pub(crate) fn client(account: &DavAccount) -> Result<CalDavClient, CaldavError> {
    CalDavClient::new(&account.base_url, Some(&account.username), Some(&account.password))
        .map_err(|e| bridge_error("Client", e))
}

/// Fail unless the CalDAV response carries a 2xx status. The lib returns the response on any
/// status, so a server-side rejection (4xx/5xx) would otherwise pass as success.
pub(crate) fn ensure_success<T>(context: &str, resp: &Response<T>) -> Result<(), CaldavError> {
    if resp.status().is_success() {
        Ok(())
    } else {
        Err(bridge_error(context, format!("HTTP {}", resp.status())))
    }
}

