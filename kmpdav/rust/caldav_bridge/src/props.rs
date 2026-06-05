//! Fetch the CalDAV collection properties that `fast-dav-rs` does not surface:
//! the `current-user-privilege-set` (RFC 3744) and the Apple `calendar-color`.
//! We issue a single `Depth: 1` PROPFIND on the calendar home-set and parse the
//! multistatus with a read-only DOM (`roxmltree`), matching on **local names**
//! so we stay agnostic to the server's namespace prefix (`D:` vs `d:` vs none).
//!
//! Best-effort: any failure yields no data and callers fall back to defaults.
//! To fetch a new property, add it to [`PROPS_BODY`] and to [`CollectionProps`].

use std::collections::HashMap;

use fast_dav_rs::{CalDavClient, Depth};
use roxmltree::{Document, Node};

use crate::error::{err, CaldavError};
use crate::models::CalendarAccessLevel;

/// Single PROPFIND body requesting every collection property we care about.
const PROPS_BODY: &str = r#"<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:A="http://apple.com/ns/ical/">
  <D:prop>
    <D:current-user-privilege-set/>
    <A:calendar-color/>
  </D:prop>
</D:propfind>"#;

/// Properties extracted from a single `<response>` of the multistatus.
pub(crate) struct CollectionProps {
    /// Local-names of the granted privileges (e.g. `"read"`, `"write"`, `"all"`).
    pub privileges: Vec<String>,
    /// Apple `calendar-color`, typically `#RRGGBB` or `#RRGGBBAA`.
    pub color: Option<String>,
}

/// PROPFIND (`Depth: 1`) the home-set and return per-collection properties,
/// keyed by normalized collection href.
pub(crate) async fn collection_props(
    cli: &CalDavClient,
    home: &str,
) -> HashMap<String, CollectionProps> {
    fetch(cli, home).await.unwrap_or_default()
}

/// Normalize an href for comparison/keying (trim whitespace + trailing slash).
pub(crate) fn normalize_href(href: &str) -> String {
    href.trim().trim_end_matches('/').to_string()
}

/// Derive the [`CalendarAccessLevel`] from a collection's granted privileges.
///
/// We rely **solely** on the `current-user-privilege-set` (RFC 3744), *not* on
/// `DAV:owner`: some servers (e.g. SabreDAV / Infomaniak) report the calendar
/// home owner as the `owner` of *every* collection — including read-only shares —
/// so `owner == principal` would wrongly mark everything as owned. The privilege
/// set, on the other hand, accurately reflects what the current user can do.
pub(crate) fn access_level(props: &CollectionProps) -> CalendarAccessLevel {
    let granted = |name: &str| props.privileges.iter().any(|p| p == name);

    // `DAV:all`, or the ability to change the ACL, means full control / ownership.
    if granted("all") || granted("write-acl") {
        return CalendarAccessLevel::Owner;
    }

    let can_write = props.privileges.iter().any(|p| {
        matches!(
            p.as_str(),
            "write" | "write-content" | "write-properties" | "bind" | "unbind"
        )
    });

    if can_write {
        CalendarAccessLevel::ReadWrite
    } else if granted("read") {
        CalendarAccessLevel::Read
    } else {
        CalendarAccessLevel::None
    }
}

async fn fetch(
    cli: &CalDavClient,
    home: &str,
) -> Result<HashMap<String, CollectionProps>, CaldavError> {
    let resp = cli
        .propfind(home, Depth::One, PROPS_BODY)
        .await
        .map_err(|e| err("PropsPropfind", e))?;

    if !resp.status().is_success() {
        return Err(CaldavError::Bridge {
            msg: format!("Props PROPFIND failed with {}", resp.status()),
        });
    }

    Ok(parse(resp.body().as_ref()))
}

/// Parse a WebDAV multistatus body into per-collection props, keyed by
/// normalized href. Any malformed body yields an empty map (best-effort).
fn parse(xml: &[u8]) -> HashMap<String, CollectionProps> {
    let text = String::from_utf8_lossy(xml);
    let doc = match Document::parse(&text) {
        Ok(doc) => doc,
        Err(_) => return HashMap::new(),
    };

    doc.root()
        .descendants()
        .filter(|n| local_name(n) == "response")
        .filter_map(parse_response)
        .map(|(href, props)| (normalize_href(&href), props))
        .collect()
}

/// Extract the href and known properties from a single `<response>`.
fn parse_response(response: Node) -> Option<(String, CollectionProps)> {
    // The collection href is the `<href>` that is a direct child of `<response>`.
    let href = response
        .children()
        .find(|n| local_name(n) == "href")
        .and_then(|n| n.text())
        .map(str::trim)
        .filter(|s| !s.is_empty())?
        .to_string();

    // Every element nested under a `<privilege>` is a granted privilege.
    let privileges = response
        .descendants()
        .filter(|n| n.is_element() && has_ancestor(n, "privilege"))
        .map(|n| local_name(&n))
        .collect();

    let color = text_prop(response, "calendar-color");

    Some((href, CollectionProps { privileges, color }))
}

/// Read a simple leaf-text property (e.g. `calendar-color`, `getctag`) by its
/// local name, trimmed; `None` when absent or empty.
fn text_prop(response: Node, name: &str) -> Option<String> {
    response
        .descendants()
        .find(|n| local_name(n) == name)
        .and_then(|n| n.text())
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_string)
}

/// Lowercased local name (namespace prefix stripped) of an element node.
fn local_name(node: &Node) -> String {
    node.tag_name().name().to_ascii_lowercase()
}

/// Whether any ancestor element of `node` has the given local name.
fn has_ancestor(node: &Node, name: &str) -> bool {
    node.ancestors()
        .skip(1) // skip the node itself
        .any(|a| a.is_element() && a.tag_name().name().eq_ignore_ascii_case(name))
}
