// caldav_bridge – Thin UniFFI wrapper around libdav for KMP.

use std::sync::Once;
use hyper_rustls::HttpsConnectorBuilder;
use icalendar::{Calendar, Component, CalendarComponent};
use libdav::auth::{Auth, Password};
use libdav::dav::WebDavClient;
use libdav::CalDavClient;
use tokio::runtime::Runtime;

uniffi::setup_scaffolding!();

// ── Error ────────────────────────────────────────────────────────────────

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CaldavError {
    #[error("{msg}")]
    Bridge { msg: String },
}

// ── Records (→ Kotlin data classes via UniFFI) ───────────────────────────

#[derive(uniffi::Record)]
pub struct CalendarEntry {
    pub url: String,
    pub display_name: String,
}

#[derive(uniffi::Record)]
pub struct EventEntry {
    pub url: String,
    pub etag: String,
    /// Raw iCS data (needed for update/delete round-trips).
    pub ics_data: String,
    /// Parsed fields from VEVENT.
    pub uid: String,
    pub summary: Option<String>,
    pub description: Option<String>,
    pub location: Option<String>,
    /// ISO 8601 date-time strings (e.g. "20260526T100000Z").
    pub dtstart: Option<String>,
    pub dtend: Option<String>,
    pub rrule: Option<String>,
    pub status: Option<String>,
}

#[derive(uniffi::Record)]
pub struct MutateResult {
    pub url: String,
    pub etag: String,
}

// ── Internal helpers ─────────────────────────────────────────────────────

static CRYPTO_INIT: Once = Once::new();

type Connector = hyper_rustls::HttpsConnector<hyper_util::client::legacy::connect::HttpConnector>;

fn make_client(base_url: &str, username: &str, password: &str) -> Result<(Runtime, CalDavClient<Connector>), CaldavError> {
    CRYPTO_INIT.call_once(|| {
        rustls::crypto::ring::default_provider().install_default().expect("CryptoProvider init");
    });

    let rt = Runtime::new().map_err(|e| CaldavError::Bridge { msg: format!("Tokio: {e}") })?;

    let uri: http::Uri = base_url.parse().map_err(|e| CaldavError::Bridge { msg: format!("Bad URL: {e}") })?;

    let auth = Auth::Basic {
        username: username.to_string(),
        password: Some(Password::from(password.to_string())),
    };

    let https = HttpsConnectorBuilder::new()
        .with_webpki_roots()
        .https_only()
        .enable_http1()
        .build();

    Ok((rt, CalDavClient::new(WebDavClient::new(uri, auth, https))))
}

fn err(context: &str, e: impl std::fmt::Display) -> CaldavError {
    CaldavError::Bridge { msg: format!("{context}: {e}") }
}

/// Extract a property value by name from a VEVENT component.
fn prop(event: &icalendar::Event, name: &str) -> Option<String> {
    event.property_value(name).map(|s| s.to_string())
}

/// Parse raw iCS data into an EventEntry with typed fields.
fn parse_ics(url: String, etag: String, ics_data: String) -> EventEntry {
    let parsed: Calendar = ics_data.parse().unwrap_or_default();

    // Find the first VEVENT in the calendar.
    let vevent = parsed.components.iter().find_map(|c| {
        if let CalendarComponent::Event(e) = c { Some(e) } else { None }
    });

    match vevent {
        Some(ev) => EventEntry {
            url,
            etag,
            uid: prop(ev, "UID").unwrap_or_default(),
            summary: prop(ev, "SUMMARY"),
            description: prop(ev, "DESCRIPTION"),
            location: prop(ev, "LOCATION"),
            dtstart: prop(ev, "DTSTART"),
            dtend: prop(ev, "DTEND"),
            rrule: prop(ev, "RRULE"),
            status: prop(ev, "STATUS"),
            ics_data,
        },
        None => EventEntry {
            url,
            etag,
            ics_data,
            uid: String::new(),
            summary: None, description: None, location: None,
            dtstart: None, dtend: None, rrule: None, status: None,
        },
    }
}

// ── Exported functions (called from Kotlin via UniFFI bindings) ──────────

#[uniffi::export]
pub fn discover(base_url: &str, username: &str, password: &str) -> Result<Vec<CalendarEntry>, CaldavError> {
    let (rt, client) = make_client(base_url, username, password)?;

    rt.block_on(async {
        let principal = client.find_current_user_principal().await.map_err(|e| err("Principal", e))?
            .ok_or_else(|| CaldavError::Bridge { msg: "No current-user-principal returned".into() })?;

        let home_sets = client.find_calendar_home_set(&principal).await.map_err(|e| err("HomeSet", e))?;

        let mut calendars = Vec::new();
        for hs in &home_sets {
            for c in client.find_calendars(hs).await.map_err(|e| err("FindCalendars", e))? {
                let name = c.href.trim_end_matches('/').rsplit('/').next().unwrap_or(&c.href).to_string();
                calendars.push(CalendarEntry { url: c.href.clone(), display_name: name });
            }
        }
        Ok(calendars)
    })
}

#[uniffi::export]
pub fn fetch_events(base_url: &str, username: &str, password: &str, calendar_url: &str) -> Result<Vec<EventEntry>, CaldavError> {
    let (rt, client) = make_client(base_url, username, password)?;

    rt.block_on(async {
        let listed = client.list_resources(calendar_url).await.map_err(|e| err("List", e))?;
        let hrefs: Vec<&str> = listed.iter().map(|r| r.href.as_str()).collect();
        if hrefs.is_empty() { return Ok(vec![]); }

        let resources = client.get_calendar_resources(calendar_url, &hrefs).await.map_err(|e| err("Fetch", e))?;
        Ok(resources.into_iter().filter_map(|r| {
            r.content.ok().map(|c| parse_ics(r.href, c.etag, c.data))
        }).collect())
    })
}

#[uniffi::export]
pub fn create_event(base_url: &str, username: &str, password: &str, calendar_url: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let (rt, client) = make_client(base_url, username, password)?;

    rt.block_on(async {
        let uid = uuid::Uuid::new_v4();
        let path = format!("{}/{uid}.ics", calendar_url.trim_end_matches('/'));
        let etag = client.create_resource(&path, ics_data.as_bytes().to_vec(), b"text/calendar")
            .await.map_err(|e| err("Create", e))?;
        Ok(MutateResult { url: path, etag: etag.unwrap_or_default() })
    })
}

#[uniffi::export]
pub fn update_event(base_url: &str, username: &str, password: &str, event_url: &str, etag: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let (rt, client) = make_client(base_url, username, password)?;

    rt.block_on(async {
        let new_etag = client.update_resource(event_url, ics_data.as_bytes().to_vec(), etag, b"text/calendar")
            .await.map_err(|e| err("Update", e))?;
        Ok(MutateResult { url: event_url.to_string(), etag: new_etag.unwrap_or_default() })
    })
}

#[uniffi::export]
pub fn delete_event(base_url: &str, username: &str, password: &str, event_url: &str, etag: &str) -> Result<(), CaldavError> {
    let (rt, client) = make_client(base_url, username, password)?;

    rt.block_on(async {
        client.delete(event_url, etag).await.map_err(|e| err("Delete", e))
    })
}
