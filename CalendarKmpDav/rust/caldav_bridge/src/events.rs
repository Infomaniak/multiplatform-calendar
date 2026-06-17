//! Event operations (fetch / create / update / delete) and iCalendar parsing.

use icalendar::{Calendar, CalendarComponent, Component, Property};

use crate::client::{client, rt};
use crate::error::{err, CaldavError};
use crate::models::{DavAccount, EventEdit, EventEntry, MutateResult};

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

/// Apply [`EventEdit`] onto an existing VEVENT, preserving every property we don't touch.
///
/// Replaces the edited content fields, then refreshes the revision metadata. Returns the
/// re-serialized iCalendar object.
#[uniffi::export]
pub fn patch_event_ics(ics_data: &str, edit: EventEdit) -> Result<String, CaldavError> {
    let mut calendar: Calendar = ics_data.parse().map_err(|e| err("Patch", e))?;

    let event = calendar
        .components
        .iter_mut()
        .find_map(|component| match component {
            CalendarComponent::Event(event) => Some(event),
            _ => None,
        })
        .ok_or_else(|| CaldavError::Bridge { msg: "Patch: no VEVENT in ICS".to_string() })?;

    apply_edited_fields(event, &edit);
    bump_revision(event, &edit.stamp);

    Ok(calendar.to_string())
}

/// Replace the user-edited content fields (keeps DTEND/DURATION mutually exclusive).
fn apply_edited_fields(event: &mut icalendar::Event, edit: &EventEdit) {
    set_or_clear(event, "SUMMARY", edit.summary.as_deref());
    set_or_clear(event, "LOCATION", edit.location.as_deref());
    set_or_clear(event, "DESCRIPTION", edit.description.as_deref());

    event.remove_property("DTSTART");
    event.remove_property("DTEND");
    event.remove_property("DURATION");
    if edit.all_day {
        event.append_property(Property::new("DTSTART", &edit.dtstart).add_parameter("VALUE", "DATE").done());
        if let Some(dtend) = edit.dtend.as_deref() {
            event.append_property(Property::new("DTEND", dtend).add_parameter("VALUE", "DATE").done());
        }
    } else {
        event.add_property("DTSTART", &edit.dtstart);
        if let Some(dtend) = edit.dtend.as_deref() {
            event.add_property("DTEND", dtend);
        }
    }
}

/// Build a fresh iCalendar object (one VEVENT) from [`EventEdit`], with a new UID and SEQUENCE 0.
#[uniffi::export]
pub fn build_event_ics(edit: EventEdit) -> Result<String, CaldavError> {
    let mut event = icalendar::Event::new();
    event.add_property("UID", uuid::Uuid::new_v4().to_string());
    apply_edited_fields(&mut event, &edit);
    bump_revision(&mut event, &edit.stamp);

    let mut calendar = Calendar::new();
    calendar.push(event);
    Ok(calendar.to_string())
}

/// Refresh the revision metadata: set SEQUENCE (bump existing, else 0) and DTSTAMP/LAST-MODIFIED.
fn bump_revision(event: &mut icalendar::Event, stamp: &str) {
    let next_sequence = match event
        .property_value("SEQUENCE")
        .and_then(|value| value.trim().parse::<u32>().ok())
    {
        Some(current) => {
            event.remove_property("SEQUENCE");
            current + 1
        }
        None => 0,
    };
    event.add_property("SEQUENCE", next_sequence.to_string());

    event.remove_property("DTSTAMP");
    event.add_property("DTSTAMP", stamp);
    event.remove_property("LAST-MODIFIED");
    event.add_property("LAST-MODIFIED", stamp);
}

fn set_or_clear(event: &mut icalendar::Event, key: &str, value: Option<&str>) {
    event.remove_property(key);
    if let Some(value) = value {
        event.add_property(key, value);
    }
}

/// Fetch all events (iCalendar resources) inside a calendar.
#[uniffi::export]
pub fn fetch_events(account: &DavAccount, calendar_url: &str) -> Result<Vec<EventEntry>, CaldavError> {
    let rt = rt()?;
    let cli = client(account)?;

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
pub fn create_event(account: &DavAccount, calendar_url: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let rt = rt()?;
    let cli = client(account)?;
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
pub fn update_event(account: &DavAccount, event_url: &str, etag: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let rt = rt()?;
    let cli = client(account)?;
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
pub fn delete_event(account: &DavAccount, event_url: &str, etag: &str) -> Result<(), CaldavError> {
    let rt = rt()?;
    let cli = client(account)?;

    rt.block_on(async {
        cli.delete_if_match(event_url, etag).await.map_err(|e| err("Delete", e))?;
        Ok(())
    })
}

