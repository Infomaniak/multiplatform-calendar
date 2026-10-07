//! Principal properties that `fast-dav-rs` does not surface.

use fast_dav_rs::{CalDavClient, Depth};
use roxmltree::Document;

use crate::client::ensure_success;
use crate::error::{map_fast_dav_error, CaldavError};
use crate::events::strip_mailto;
use crate::props::{has_ancestor, local_name};

const USER_EMAILS_BODY: &str = r#"<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:prop>
    <C:calendar-user-address-set/>
  </D:prop>
</D:propfind>"#;

/// The emails of the user: the `mailto:` entries of the principal's `calendar-user-address-set` (RFC 6638).
pub(crate) async fn user_emails(cli: &CalDavClient, principal: &str) -> Result<Vec<String>, CaldavError> {
    let resp = cli
        .propfind(principal, Depth::Zero, USER_EMAILS_BODY)
        .await
        .map_err(|error| map_fast_dav_error("UserEmails", error))?;

    ensure_success("UserEmails", &resp)?;

    Ok(parse_user_emails(resp.body().as_ref()))
}

/// Keeps the `mailto:` hrefs of the multistatus, without their scheme and deduplicated ignoring case.
pub(crate) fn parse_user_emails(xml: &[u8]) -> Vec<String> {
    let text = String::from_utf8_lossy(xml);
    let Ok(doc) = Document::parse(&text) else {
        return Vec::new();
    };

    let mut emails: Vec<String> = Vec::new();
    let hrefs = doc
        .descendants()
        .filter(|n| local_name(n) == "href" && has_ancestor(n, "calendar-user-address-set"))
        .filter_map(|n| n.text())
        .map(str::trim);
    for href in hrefs {
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
