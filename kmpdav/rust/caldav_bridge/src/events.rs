//! Event operations (fetch / create / update / delete) and iCalendar parsing.

use icalendar::{Calendar, CalendarComponent, Component};

use crate::client::{client, rt};
use crate::error::{err, CaldavError};
use crate::models::{EventEntry, MutateResult};

/// Read a single iCalendar property value as an owned [`String`].
fn prop(event: &icalendar::Event, name: &str) -> Option<String> {
    event.property_value(name).map(|s| s.to_string())
}

/// Parse raw iCS data into an [`EventEntry`], extracting the first `VEVENT`.
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
            duration: prop(ev, "DURATION"),
            created: prop(ev, "CREATED"),
            last_modified: prop(ev, "LAST-MODIFIED"),
            dtstamp: prop(ev, "DTSTAMP"),
            rrule: prop(ev, "RRULE"),
            status: prop(ev, "STATUS"),
            transp: prop(ev, "TRANSP"),
            classification: prop(ev, "CLASS"),
            priority: prop(ev, "PRIORITY"),
            sequence: prop(ev, "SEQUENCE"),
            categories: prop(ev, "CATEGORIES"),
            organizer: prop(ev, "ORGANIZER"),
            ics_data,
        },
        None => EventEntry {
            url,
            etag,
            ics_data,
            uid: String::new(),
            summary: None, description: None, location: None,
            dtstart: None, dtend: None, duration: None, created: None, last_modified: None, dtstamp: None,
            rrule: None, status: None, transp: None, classification: None, priority: None,
            sequence: None, categories: None, organizer: None,
        },
    }
}

/// Fetch all events (iCalendar resources) inside a calendar.
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

/// Create a new event. Returns the server-assigned URL + etag.
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

/// Update an existing event (identified by its URL + etag for conflict detection).
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

/// Delete an event (identified by its URL + etag).
#[uniffi::export]
pub fn delete_event(base_url: &str, username: &str, password: &str, event_url: &str, etag: &str) -> Result<(), CaldavError> {
    let rt = rt()?;
    let cli = client(base_url, username, password)?;

    rt.block_on(async {
        cli.delete_if_match(event_url, etag).await.map_err(|e| err("Delete", e))?;
        Ok(())
    })
}

