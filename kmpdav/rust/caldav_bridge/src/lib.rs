// caldav_bridge – Thin UniFFI wrapper around fast-dav-rs for KMP.

use fast_dav_rs::CalDavClient;
use icalendar::{Calendar, Component, CalendarComponent};
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
    pub color: Option<String>,
    pub description: Option<String>,
    pub ctag: Option<String>,
}

#[derive(uniffi::Record)]
pub struct EventEntry {
    pub url: String,
    pub etag: String,
    pub ics_data: String,
    pub uid: String,
    pub summary: Option<String>,
    pub description: Option<String>,
    pub location: Option<String>,
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

fn rt() -> Result<Runtime, CaldavError> {
    Runtime::new().map_err(|e| CaldavError::Bridge { msg: format!("Tokio: {e}") })
}

fn client(base_url: &str, username: &str, password: &str) -> Result<CalDavClient, CaldavError> {
    CalDavClient::new(base_url, Some(username), Some(password))
        .map_err(|e| CaldavError::Bridge { msg: format!("Client: {e}") })
}

fn err(context: &str, e: impl std::fmt::Display) -> CaldavError {
    CaldavError::Bridge { msg: format!("{context}: {e}") }
}

fn prop(event: &icalendar::Event, name: &str) -> Option<String> {
    event.property_value(name).map(|s| s.to_string())
}

fn parse_ics(url: String, etag: String, ics_data: String) -> EventEntry {
    let parsed: Calendar = ics_data.parse().unwrap_or_default();

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
    let rt = rt()?;
    let cli = client(base_url, username, password)?;

    rt.block_on(async {
        let principal = cli.discover_current_user_principal().await
            .map_err(|e| err("Principal", e))?
            .ok_or_else(|| CaldavError::Bridge { msg: "No current-user-principal".into() })?;

        let homes = cli.discover_calendar_home_set(&principal).await
            .map_err(|e| err("HomeSet", e))?;

        let mut calendars = Vec::new();
        for home in &homes {
            for cal in cli.list_calendars(home).await.map_err(|e| err("ListCalendars", e))? {
                calendars.push(CalendarEntry {
                    url: cal.href.clone(),
                    display_name: cal.displayname.unwrap_or_else(|| {
                        cal.href.trim_end_matches('/').rsplit('/').next()
                            .unwrap_or(&cal.href).to_string()
                    }),
                    color: cal.color.clone(),
                    description: cal.description.clone(),
                    ctag: cal.sync_token.clone(),
                });
            }
        }
        Ok(calendars)
    })
}

#[uniffi::export]
pub fn fetch_events(base_url: &str, username: &str, password: &str, calendar_url: &str) -> Result<Vec<EventEntry>, CaldavError> {
    let rt = rt()?;
    let cli = client(base_url, username, password)?;

    rt.block_on(async {
        let objects = cli.calendar_query_timerange(calendar_url, "VEVENT", None, None, true)
            .await.map_err(|e| err("Query", e))?;

        Ok(objects.into_iter().filter_map(|obj| {
            let etag = obj.etag.unwrap_or_default();
            obj.calendar_data.map(|data| parse_ics(obj.href, etag, data))
        }).collect())
    })
}

#[uniffi::export]
pub fn create_event(base_url: &str, username: &str, password: &str, calendar_url: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let rt = rt()?;
    let cli = client(base_url, username, password)?;
    let body = bytes::Bytes::from(ics_data.as_bytes().to_vec());

    rt.block_on(async {
        let uid = uuid::Uuid::new_v4();
        let path = format!("{}/{uid}.ics", calendar_url.trim_end_matches('/'));
        let resp = cli.put_if_none_match(&path, body)
            .await.map_err(|e| err("Create", e))?;
        let etag = resp.headers()
            .get("etag")
            .and_then(|v| v.to_str().ok())
            .unwrap_or_default()
            .to_string();
        Ok(MutateResult { url: path, etag })
    })
}

#[uniffi::export]
pub fn update_event(base_url: &str, username: &str, password: &str, event_url: &str, etag: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let rt = rt()?;
    let cli = client(base_url, username, password)?;
    let body = bytes::Bytes::from(ics_data.as_bytes().to_vec());

    rt.block_on(async {
        let resp = cli.put_if_match(event_url, body, etag)
            .await.map_err(|e| err("Update", e))?;
        let new_etag = resp.headers()
            .get("etag")
            .and_then(|v| v.to_str().ok())
            .unwrap_or_default()
            .to_string();
        Ok(MutateResult { url: event_url.to_string(), etag: new_etag })
    })
}

#[uniffi::export]
pub fn delete_event(base_url: &str, username: &str, password: &str, event_url: &str, etag: &str) -> Result<(), CaldavError> {
    let rt = rt()?;
    let cli = client(base_url, username, password)?;

    rt.block_on(async {
        cli.delete_if_match(event_url, etag).await.map_err(|e| err("Delete", e))?;
        Ok(())
    })
}
