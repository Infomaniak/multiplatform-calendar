//! Internal helpers to build the Tokio runtime and the CalDAV client.
//!
//! These are crate-private building blocks shared by the calendar and event
//! operation modules.

use fast_dav_rs::CalDavClient;
use tokio::runtime::Runtime;

use crate::error::CaldavError;

/// Create a multi-thread Tokio runtime used to drive the async CalDAV calls.
pub(crate) fn rt() -> Result<Runtime, CaldavError> {
    Runtime::new().map_err(|e| CaldavError::Bridge { msg: format!("Tokio: {e}") })
}

/// Build a [`CalDavClient`] authenticated with the given credentials.
pub(crate) fn client(base_url: &str, username: &str, password: &str) -> Result<CalDavClient, CaldavError> {
    CalDavClient::new(base_url, Some(username), Some(password))
        .map_err(|e| CaldavError::Bridge { msg: format!("Client: {e}") })
}

