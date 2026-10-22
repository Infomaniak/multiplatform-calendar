//! Fetch the `current-user-privilege-set` (RFC 3744) of the calendar collections, which `list_calendars` does not
//! request. Best-effort: any failure yields no data and callers fall back to defaults.

use fast_dav_rs::Privilege;

use crate::models::CalendarAccessLevel;

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
