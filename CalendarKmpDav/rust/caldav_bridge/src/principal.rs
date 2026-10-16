//! Principal properties that `fast-dav-rs` does not surface as-is.

use fast_dav_rs::CalDavClient;

use crate::error::{map_fast_dav_error, CaldavError};
use crate::events::strip_mailto;

/// The emails of the user: the `mailto:` entries of the principal's `calendar-user-address-set` (RFC 6638).
pub(crate) async fn user_emails(cli: &CalDavClient, principal: &str) -> Result<Vec<String>, CaldavError> {
    let endpoints = cli
        .discover_schedule_endpoints(principal)
        .await
        .map_err(|error| map_fast_dav_error("UserEmails", error))?;

    Ok(emails_from_addresses(&endpoints.user_addresses))
}

/// Keeps the `mailto:` addresses, without their scheme and deduplicated ignoring case.
pub(crate) fn emails_from_addresses(addresses: &[String]) -> Vec<String> {
    let mut emails: Vec<String> = Vec::new();
    for href in addresses {
        let email = strip_mailto(href);
        if email.len() == href.len() || email.is_empty() {
            continue;
        }
        if !emails.iter().any(|known| known.eq_ignore_ascii_case(&email)) {
            emails.push(email);
        }
    }
    emails
}
