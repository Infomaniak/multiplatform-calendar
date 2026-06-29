//! Calendar discovery operations.

use crate::client::{client, rt};
use crate::error::{err, CaldavError};
use crate::models::{CalendarAccessLevel, CalendarEdit, CalendarEntry, DavAccount};
use crate::props::{access_level, collection_props, normalize_href};
use roxmltree::Document;

/// Discover all calendars for the given credentials.
#[uniffi::export]
pub fn discover(account: DavAccount) -> Result<Vec<CalendarEntry>, CaldavError> {
    let rt = rt()?;
    let cli = client(&account)?;

    rt.block_on(async {
        let principal = cli.discover_current_user_principal().await
            .map_err(|e| err("Principal", e))?
            .ok_or_else(|| CaldavError::Bridge { msg: "No current-user-principal".into() })?;

        let homes = cli.discover_calendar_home_set(&principal).await
            .map_err(|e| err("HomeSet", e))?;

        let mut calendars = Vec::new();
        for home in &homes {
            let props = collection_props(&cli, home).await;
            for cal in cli.list_calendars(home).await.map_err(|e| err("ListCalendars", e))? {
                let entry_props = props.get(normalize_href(&cal.href).as_str());
                let access_level = entry_props
                    .map(access_level)
                    .unwrap_or(CalendarAccessLevel::ReadWrite);
                // Prefer the color from our PROPFIND, fall back to the listing's.
                let color = entry_props
                    .and_then(|p| p.color.clone())
                    .or_else(|| cal.color.clone());
                calendars.push(CalendarEntry {
                    url: cal.href.clone(),
                    display_name: cal.displayname.unwrap_or_else(|| {
                        cal.href.trim_end_matches('/').rsplit('/').next()
                            .unwrap_or(&cal.href).to_string()
                    }),
                    color,
                    description: cal.description.clone(),
                    ctag: cal.sync_token.clone(),
                    access_level,
                });
            }
        }
        Ok(calendars)
    })
}

/// Update editable properties on a calendar collection (PROPPATCH).
///
/// Only the fields present in [`CalendarEdit`] are sent; absent (`None`) fields are left
/// untouched on the server. Fails when the server responds with a non-success multistatus or any
/// of the requested properties is rejected.
#[uniffi::export]
pub fn update_calendar(
    account: DavAccount,
    calendar_url: &str,
    edit: CalendarEdit,
) -> Result<(), CaldavError> {
    if edit.display_name.is_none() && edit.color.is_none() {
        return Ok(());
    }

    let rt = rt()?;
    let cli = client(&account)?;
    let body = build_proppatch_body(&edit);

    rt.block_on(async {
        let resp = cli.proppatch(calendar_url, &body).await.map_err(|e| err("Proppatch", e))?;
        if !resp.status().is_success() {
            return Err(CaldavError::Bridge {
                msg: format!("PROPPATCH failed with {}", resp.status()),
            });
        }
        check_propstat_success(resp.body().as_ref())
    })
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
    let text = String::from_utf8_lossy(xml);
    let doc = Document::parse(&text).map_err(|e| CaldavError::Bridge {
        msg: format!("PROPPATCH: malformed multistatus: {e}"),
    })?;

    for node in doc.root().descendants() {
        if !node.is_element() || !node.tag_name().name().eq_ignore_ascii_case("status") {
            continue;
        }
        let parent_is_propstat = node
            .parent()
            .map(|p| p.tag_name().name().eq_ignore_ascii_case("propstat"))
            .unwrap_or(false);
        if !parent_is_propstat {
            continue;
        }
        let status = node.text().unwrap_or("").trim();
        // Expected form: "HTTP/1.1 200 OK". Reject anything that isn't 2xx.
        let code = status.split_whitespace().nth(1).and_then(|s| s.parse::<u16>().ok());
        if !matches!(code, Some(200..=299)) {
            return Err(CaldavError::Bridge {
                msg: format!("PROPPATCH rejected: {status}"),
            });
        }
    }
    Ok(())
}

