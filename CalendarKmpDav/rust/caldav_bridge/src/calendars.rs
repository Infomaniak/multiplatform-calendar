//! Calendar discovery operations.

use crate::client::{client, ensure_success};
use crate::error::{bridge_error, map_fast_dav_error, CaldavError};
use crate::models::{CalendarAccessLevel, CalendarEdit, CalendarEntry, DavAccount, DiscoveryEntry};
use crate::principal::user_emails;
use crate::props::{access_level, normalize_href};
use fast_dav_rs::{parse_multistatus_bytes, Depth};
use std::collections::HashMap;

const CALENDARS_PROPFIND: &str = r#"<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"
            xmlns:A="http://apple.com/ns/ical/" xmlns:I="http://infomaniak.com/ns">
  <D:prop>
    <D:displayname/>
    <C:calendar-description/>
    <C:calendar-timezone/>
    <A:calendar-color/>
    <C:supported-calendar-component-set/>
    <C:max-resource-size/>
    <C:supported-calendar-data/>
    <C:max-attendees-per-instance/>
    <D:getetag/>
    <D:resourcetype/>
    <D:sync-token/>
    <D:current-user-privilege-set/>
    <I:account-id/>
    <I:calendar-type/>
    <I:default-calendar/>
  </D:prop>
</D:propfind>"#;

#[derive(Default)]
pub(crate) struct CalendarProps {
    // Infomaniak-specific properties.
    pub(crate) account_id: Option<i64>,
    pub(crate) calendar_type: String,
    pub(crate) default_calendar: bool,

    // Standard DAV properties.
    pub(crate) has_privileges: bool,
}

fn default_calendar_type(value: String) -> String {
    if value.is_empty() { "basic".to_owned() } else { value }
}

fn calendar_display_name(href: &str, display_name: Option<String>) -> String {
    display_name.unwrap_or_else(|| {
        href.trim_end_matches('/').rsplit('/').next()
            .unwrap_or(href).to_owned()
    })
}

// Only successful DAV propstats count; match by href, never by response order.
pub(crate) fn infomaniak_properties(xml: &str) -> Result<HashMap<String, CalendarProps>, CaldavError> {
    const DAV: &str = "DAV:";
    const IK: &str = "http://infomaniak.com/ns";
    let doc = roxmltree::Document::parse(xml)
        .map_err(|e| bridge_error("ListCalendars", format!("invalid XML: {e}")))?;

    let mut result = HashMap::new();
    for response in doc.descendants().filter(|n| n.has_tag_name((DAV, "response"))) {
        let Some(href) = response.children()
            .find(|n| n.has_tag_name((DAV, "href")))
            .and_then(|n| n.text()) else { continue };
        let mut props = CalendarProps::default();

        for propstat in response.children().filter(|n| n.has_tag_name((DAV, "propstat"))) {
            let status = propstat.children()
                .find(|n| n.has_tag_name((DAV, "status")))
                .and_then(|n| n.text()).unwrap_or("");

            if status.split_whitespace().nth(1) != Some("200") { continue; }

            let Some(prop) = propstat.children().find(|n| n.has_tag_name((DAV, "prop"))) else { continue };

            // Standard DAV properties
            props.has_privileges |= prop.children().any(|node| {
                node.has_tag_name((DAV, "current-user-privilege-set"))
            });

            // Infomaniak-specific properties
            let value = |name| prop.children()
                .find(|n| n.has_tag_name((IK, name)))
                .and_then(|n| n.text()).map(str::trim);

            if let Some(id) = value("account-id") {
                props.account_id = id.parse::<i64>().ok();
            }
            if let Some(kind) = value("calendar-type") {
                props.calendar_type = kind.to_owned();
            }
            if let Some(default) = value("default-calendar") {
                props.default_calendar = default == "1";
            }
        }
        result.insert(normalize_href(href), props);
    }
    Ok(result)
}

fn parse_discovered_calendars(bytes: &[u8]) -> Result<Vec<CalendarEntry>, CaldavError> {
    let items = parse_multistatus_bytes(bytes)
        .map_err(|error| map_fast_dav_error("ListCalendars", error))?
        .items;
    let xml = std::str::from_utf8(bytes)
        .map_err(|error| bridge_error("ListCalendars", format!("invalid UTF-8: {error}")))?;
    let mut custom = infomaniak_properties(xml)?;

    Ok(items.into_iter().filter(|item| item.is_calendar).map(|item| {
        let extra = custom.remove(&normalize_href(&item.href)).unwrap_or_default();
        let access_level = if extra.has_privileges {
            access_level(&item.current_user_privileges)
        } else {
            CalendarAccessLevel::ReadWrite
        };

        CalendarEntry {
            display_name: calendar_display_name(&item.href, item.displayname),
            url: item.href,
            color: item.calendar_color,
            description: item.calendar_description
                .map(|d| d.trim().to_owned())
                .filter(|d| !d.is_empty()),
            ctag: item.sync_token,
            access_level,
            account_id: extra.account_id,
            calendar_type: default_calendar_type(extra.calendar_type),
            default_calendar: extra.default_calendar,
        }
    }).collect())
}

/// Discover the user's emails and all calendars for the given credentials.
#[uniffi::export(async_runtime = "tokio")]
pub async fn discover(account: DavAccount) -> Result<DiscoveryEntry, CaldavError> {
    let cli = client(&account)?;

    let principal = cli.discover_current_user_principal().await
        .map_err(|error| map_fast_dav_error("Principal", error))?
        .ok_or_else(|| bridge_error("Principal", "no current-user-principal"))?;

    // Best-effort: the emails only serve to find the user among the attendees.
    let user_emails = user_emails(&cli, &principal).await.ok();

    let homes = cli.discover_calendar_home_set(&principal).await
        .map_err(|error| map_fast_dav_error("HomeSet", error))?;

    let mut calendars = Vec::new();
    for home in &homes {
        let resp = cli.propfind(home, Depth::One, CALENDARS_PROPFIND).await
            .map_err(|error| map_fast_dav_error("ListCalendars", error))?;

        ensure_success("ListCalendars", &resp)?;

        calendars.extend(parse_discovered_calendars(resp.body().as_ref())?);
    }

    Ok(DiscoveryEntry { user_emails, calendars })
}
/// Update editable properties on a calendar collection (PROPPATCH).
///
/// Only the fields present in [`CalendarEdit`] are sent; absent (`None`) fields are left
/// untouched on the server. Fails when the server responds with a non-success multistatus or any
/// of the requested properties is rejected.
#[uniffi::export(async_runtime = "tokio")]
pub async fn update_calendar(
    account: DavAccount,
    calendar_url: &str,
    edit: CalendarEdit,
) -> Result<(), CaldavError> {
    if edit.display_name.is_none() && edit.color.is_none() {
        return Ok(());
    }

    let cli = client(&account)?;
    let body = build_proppatch_body(&edit);

    let resp = cli.proppatch(calendar_url, &body).await
        .map_err(|error| map_fast_dav_error("Proppatch", error))?;
    ensure_success("PROPPATCH", &resp)?;
    check_propstat_success(resp.body().as_ref())
}

/// Build a PROPPATCH request body containing only the props present in [`edit`].
fn build_proppatch_body(edit: &CalendarEdit) -> String {
    let mut props = String::new();
    if let Some(name) = &edit.display_name {
        props.push_str(&format!("      <D:displayname>{}</D:displayname>\n", escape_xml(name)));
    }
    if let Some(color) = &edit.color {
        props.push_str(&format!("      <A:calendar-color>{}</A:calendar-color>\n", escape_xml(color)));
    }
    format!(
        r#"<?xml version="1.0" encoding="utf-8"?>
<D:propertyupdate xmlns:D="DAV:" xmlns:A="http://apple.com/ns/ical/">
  <D:set>
    <D:prop>
        {props}
    </D:prop>
  </D:set>
</D:propertyupdate>"#,
    )
}

/// Minimal XML text escape (`& < > " '`). Sufficient for PCDATA contents we send.
fn escape_xml(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for c in input.chars() {
        match c {
            '&' => out.push_str("&amp;"),
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            '"' => out.push_str("&quot;"),
            '\'' => out.push_str("&apos;"),
            _ => out.push(c),
        }
    }
    out
}

/// Parse a PROPPATCH multistatus body and fail when any `<propstat>` carries a non-2xx status.
fn check_propstat_success(xml: &[u8]) -> Result<(), CaldavError> {
    let items = parse_multistatus_bytes(xml)
        .map_err(|e| bridge_error("Proppatch", format!("malformed multistatus: {e}")))?
        .items;

    let mut found_propstat = false;
    for propstat in items.iter().flat_map(|item| &item.propstats) {
        found_propstat = true;
        let status = propstat.status.as_deref().unwrap_or("");
        // Expected form: "HTTP/1.1 200 OK". Reject anything that isn't 2xx.
        let code = status.split_whitespace().nth(1).and_then(|s| s.parse::<u16>().ok());
        if !matches!(code, Some(200..=299)) {
            return Err(bridge_error("Proppatch", format!("rejected: {status}")));
        }
    }

    // A success requires confirmation: an empty/malformed multistatus is not implicit success.
    if !found_propstat {
        return Err(bridge_error("Proppatch", "no propstat in multistatus"));
    }

    Ok(())
}

