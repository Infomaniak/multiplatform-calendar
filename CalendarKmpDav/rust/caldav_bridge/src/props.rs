//! Fetch the `current-user-privilege-set` (RFC 3744) of the calendar collections, which `list_calendars` does not
//! request. Best-effort: any failure yields no data and callers fall back to defaults.

use std::collections::HashMap;

use fast_dav_rs::{parse_multistatus_bytes, CalDavClient, Depth, Privilege};

use crate::client::ensure_success;
use crate::error::{map_fast_dav_error, CaldavError};
use crate::models::CalendarAccessLevel;

const PRIVILEGES_BODY: &str = r#"<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:current-user-privilege-set/>
  </D:prop>
</D:propfind>"#;

/// PROPFIND (`Depth: 1`) the home-set and return the granted privileges of each collection, keyed by normalized href.
pub(crate) async fn collection_privileges(cli: &CalDavClient, home: &str) -> HashMap<String, Vec<Privilege>> {
    fetch(cli, home).await.unwrap_or_default()
}

/// Normalize a DAV href for comparison/keying by extracting its path
/// and removing surrounding whitespace and trailing slashes.
pub(crate) fn normalize_href(href: &str) -> String {
    let href = href.trim();
    let path = href.trim()
        .parse::<http::Uri>()
        .ok()
        .map(|uri| uri.path().to_owned())
        .unwrap_or_else(|| href.to_owned());

    path.trim_end_matches('/').to_owned()
}

/// Derive the [`CalendarAccessLevel`] from a collection's granted privileges.
///
/// We rely **solely** on the `current-user-privilege-set` (RFC 3744), *not* on
/// `DAV:owner`: some servers (e.g. SabreDAV / Infomaniak) report the calendar
/// home owner as the `owner` of *every* collection — including read-only shares —
/// so `owner == principal` would wrongly mark everything as owned. The privilege
/// set, on the other hand, accurately reflects what the current user can do.
pub(crate) fn access_level(privileges: &[Privilege]) -> CalendarAccessLevel {
    let is_owner = privileges.iter().any(|privilege| match privilege {
        Privilege::All => true,
        Privilege::Other(name) => {
            name.eq_ignore_ascii_case("all") || name.eq_ignore_ascii_case("write-acl")
        }
        _ => false,
    });

    if is_owner {
        return CalendarAccessLevel::Owner;
    }

    let can_write = privileges.iter().any(|p| {
        matches!(
            p,
            Privilege::Write | Privilege::WriteContent | Privilege::WriteProperties | Privilege::Bind | Privilege::Unbind
        )
    });

    if can_write {
        CalendarAccessLevel::ReadWrite
    } else if privileges.contains(&Privilege::Read) {
        CalendarAccessLevel::Read
    } else {
        CalendarAccessLevel::None
    }
}

async fn fetch(cli: &CalDavClient, home: &str) -> Result<HashMap<String, Vec<Privilege>>, CaldavError> {
    let resp = cli
        .propfind(home, Depth::One, PRIVILEGES_BODY)
        .await
        .map_err(|error| map_fast_dav_error("PrivilegesPropfind", error))?;

    ensure_success("PrivilegesPropfind", &resp)?;

    let items = parse_multistatus_bytes(resp.body().as_ref())
        .map_err(|error| map_fast_dav_error("PrivilegesPropfind", error))?
        .items;

    Ok(items
        .into_iter()
        .map(|item| (normalize_href(&item.href), item.current_user_privileges))
        .collect())
}
