//! Event operations (fetch / create / update / delete) and iCalendar parsing.

use icalendar::{Calendar, CalendarComponent, Component, Property};
use std::collections::HashSet;

use crate::client::{client, ensure_success, rt};
use crate::error::{bridge_error, CaldavError};
use crate::models::{
    AttendeeEntry,
    DavAccount,
    EventEdit,
    EventEntry,
    MutateResult,
	VTimeZoneSpec,
    SyncCollectionItem,
    SyncCollectionResult,
};

/// Read a single iCalendar property value as an owned [`String`].
fn prop(event: &icalendar::Event, name: &str) -> Option<String> {
    event.property_value(name).map(|s| s.to_string())
}

/// Read a single iCalendar property as `(value, TZID parameter)`.
///
/// `icalendar::Event::property_value` returns only the value and silently drops parameters, so for
/// `DTSTART`/`DTEND` (the only properties that can carry a `TZID` per RFC 5545 FORM #3) we go
/// through `properties().get()` to also surface the time-zone reference. Returns `None` if the
/// property is absent.
fn prop_with_tzid(event: &icalendar::Event, name: &str) -> (Option<String>, Option<String>) {
    match event.properties().get(name) {
        Some(p) => {
            let tzid = p.params().get("TZID").map(|v| v.value().to_string());
            (Some(p.value().to_string()), tzid)
        }
        None => (None, None),
    }
}

/// Parse raw iCS data into an [`EventEntry`], extracting the first `VEVENT`.
fn parse_ics(url: String, etag: String, ics_data: String) -> Option<EventEntry> {
    let parsed: Calendar = ics_data.parse().unwrap_or_default();

    let vevent = parsed.components.iter().find_map(|c| {
        if let CalendarComponent::Event(e) = c { Some(e) } else { None }
    });

    match vevent {
        Some(ev) => {
            let (dtstart, dtstart_tzid) = prop_with_tzid(ev, "DTSTART");
            let (dtend, dtend_tzid) = prop_with_tzid(ev, "DTEND");
            Some(EventEntry {
                url,
                etag,
                uid: prop(ev, "UID").unwrap_or_default(),
                summary: prop(ev, "SUMMARY"),
                description: prop(ev, "DESCRIPTION"),
                location: prop(ev, "LOCATION"),
                dtstart,
                dtstart_tzid,
                dtend,
                dtend_tzid,
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
                attendees: parse_attendees(ev),
                ics_data,
            })
        }
        None => {
            // TODO: Support non-VEVENT components (e.g. VTODO / VJOURNAL) instead of skipping them.
            None
        },
    }
}

/// Collect ORGANIZER + every ATTENDEE of a VEVENT into a flat participant list.
fn parse_attendees(ev: &icalendar::Event) -> Vec<AttendeeEntry> {
    use icalendar::Component;
    let mut attendees = Vec::new();
    if let Some(org) = ev.properties().get("ORGANIZER") {
        attendees.push(attendee_from_prop(org, true));
    }
    if let Some(list) = ev.multi_properties().get("ATTENDEE") {
        attendees.extend(list.iter().map(|p| attendee_from_prop(p, false)));
    }
    attendees
}

/// Build an [`AttendeeEntry`] from an ATTENDEE/ORGANIZER [`Property`], extracting CN/PARTSTAT/ROLE/RSVP.
fn attendee_from_prop(p: &Property, is_organizer: bool) -> AttendeeEntry {
    let param = |key: &str| p.get_param_as(key, |s| Some(s.to_string()));
    AttendeeEntry {
        email: strip_mailto(p.value()),
        display_name: param("CN"),
        status: param("PARTSTAT"),
        role: param("ROLE"),
        is_organizer,
        response_needed: param("RSVP").is_some_and(|v| v.eq_ignore_ascii_case("TRUE")),
    }
}

/// Strip a `mailto:` (case-insensitive) prefix to yield a bare email address.
fn strip_mailto(value: &str) -> String {
    let prefix = "mailto:";
    if value.len() >= prefix.len() && value[..prefix.len()].eq_ignore_ascii_case(prefix) {
        value[prefix.len()..].to_string()
    } else {
        value.to_string()
    }
}

/// Apply [`EventEdit`] onto an existing VEVENT, preserving every property we don't touch.
///
/// Replaces the edited content fields, then refreshes the revision metadata. Returns the
/// re-serialized iCalendar object.
#[uniffi::export]
pub fn patch_event_ics(ics_data: &str, edit: EventEdit) -> Result<String, CaldavError> {
    let mut calendar: Calendar = ics_data.parse().map_err(|e| bridge_error("Patch", e))?;

    let event = calendar
        .components
        .iter_mut()
        .find_map(|component| match component {
            CalendarComponent::Event(event) => Some(event),
            _ => None,
        })
        .ok_or_else(|| bridge_error("Patch", "no VEVENT in ICS"))?;

    apply_edited_fields(event, &edit);
    bump_revision(event, &edit.stamp);

    Ok(inject_missing_vtimezones(calendar.to_string(), &edit.timezones))
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
        event.append_property(date_time_property("DTSTART", &edit.dtstart, edit.dtstart_tzid.as_deref()));
        if let Some(dtend) = edit.dtend.as_deref() {
            event.append_property(date_time_property("DTEND", dtend, edit.dtend_tzid.as_deref()));
        }
    }
}

/// Build a `DATE-TIME` property carrying a `TZID` parameter when present (RFC 5545 FORM #3),
/// otherwise emit the value as-is (FORM #2 UTC `Z` suffix is already embedded in `value`).
fn date_time_property(key: &str, value: &str, tzid: Option<&str>) -> Property {
    let mut prop = Property::new(key, value);
    if let Some(tzid) = tzid {
        prop.add_parameter("TZID", tzid);
    }
    prop.done()
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
    Ok(inject_missing_vtimezones(calendar.to_string(), &edit.timezones))
}

/// Insert a `VTIMEZONE` component for every spec whose `TZID` isn't already declared in `ics`.
///
/// The `icalendar` builder doesn't model nested `VTIMEZONE`/`STANDARD` sub-components, so we splice
/// the blocks in textually, right before the first `BEGIN:VEVENT` (the conventional position, ahead
/// of any component that references them). Already-present TZIDs (e.g. carried by the source ICS on a
/// patch) are skipped to avoid duplicates. Uses CRLF line endings to match `icalendar`'s output.
fn inject_missing_vtimezones(ics: String, specs: &[VTimeZoneSpec]) -> String {
    if specs.is_empty() {
        return ics;
    }

    let existing = existing_vtimezone_tzids(&ics);
    let mut emitted: HashSet<&str> = HashSet::new();
    let mut blocks = String::new();
    for spec in specs {
        if existing.contains(&spec.tzid) || !emitted.insert(spec.tzid.as_str()) {
            continue;
        }
        blocks.push_str(&build_vtimezone(spec));
    }

    if blocks.is_empty() {
        return ics;
    }

    match ics.find("BEGIN:VEVENT") {
        Some(index) => {
            let mut out = String::with_capacity(ics.len() + blocks.len());
            out.push_str(&ics[..index]);
            out.push_str(&blocks);
            out.push_str(&ics[index..]);
            out
        }
        None => ics,
    }
}

/// Collect the `TZID`s already declared by `VTIMEZONE` components in `ics`.
fn existing_vtimezone_tzids(ics: &str) -> HashSet<String> {
    let mut tzids = HashSet::new();
    let mut in_vtimezone = false;
    for line in ics.lines() {
        let line = line.trim_end_matches(['\r', '\n']);
        match line {
            "BEGIN:VTIMEZONE" => in_vtimezone = true,
            "END:VTIMEZONE" => in_vtimezone = false,
            _ if in_vtimezone => {
                // Accept both `TZID:VALUE` (RFC bare form) and `TZID;PARAM=x:VALUE` (with
                // property parameters, e.g. `TZID;X-RICAL-TZSOURCE=zoneinfo:Europe/Paris`).
                if let Some(tzid) = line.strip_prefix("TZID:") {
                    tzids.insert(tzid.to_string());
                } else if let Some(rest) = line.strip_prefix("TZID;") {
                    if let Some((_, tzid)) = rest.split_once(':') {
                        tzids.insert(tzid.to_string());
                    }
                }
            }
            _ => {}
        }
    }
    tzids
}

/// Render a minimal single-offset `VTIMEZONE` block (one static `STANDARD` sub-component). CRLF-terminated.
fn build_vtimezone(spec: &VTimeZoneSpec) -> String {
    format!(
        "BEGIN:VTIMEZONE\r\n\
         TZID:{tzid}\r\n\
         BEGIN:STANDARD\r\n\
         DTSTART:19700101T000000\r\n\
         TZOFFSETFROM:{offset}\r\n\
         TZOFFSETTO:{offset}\r\n\
         END:STANDARD\r\n\
         END:VTIMEZONE\r\n",
        tzid = spec.tzid,
        offset = spec.offset,
    )
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
pub fn fetch_events(account: DavAccount, calendar_url: &str) -> Result<Vec<EventEntry>, CaldavError> {
    let rt = rt()?;
    let cli = client(&account)?;

    rt.block_on(async {
        let objects = cli.calendar_query_timerange(calendar_url, "VEVENT", None, None, true)
            .await.map_err(|e| bridge_error("Query", e))?;

        Ok(objects
            .into_iter()
            .filter_map(|obj| {
                let etag = obj.etag.unwrap_or_default();
                obj.calendar_data
                    .and_then(|data| parse_ics(obj.href, etag, data))
            })
            .collect())
    })
}

/// Fetch events overlapping a specific UTC iCalendar time range.
#[uniffi::export]
pub fn calendar_query_timerange(
    account: DavAccount,
    calendar_url: &str,
    start: &str,
    end: &str,
) -> Result<Vec<EventEntry>, CaldavError> {
    let rt = rt()?;
    let cli = client(&account)?;

    rt.block_on(async {
        let objects = cli
            .calendar_query_timerange(calendar_url, "VEVENT", Some(start), Some(end), true)
            .await
            .map_err(|e| bridge_error("CalendarQueryTimeRange", e))?;

        Ok(objects
            .into_iter()
            .filter_map(|obj| {
                let etag = obj.etag.unwrap_or_default();
                obj.calendar_data.and_then(|data| parse_ics(obj.href, etag, data))
            })
            .collect())
    })
}

/// Incrementally synchronize a calendar collection using WebDAV `sync-collection`.
#[uniffi::export]
pub fn sync_collection(
    account: DavAccount,
    calendar_url: &str,
    sync_token: Option<String>,
) -> Result<SyncCollectionResult, CaldavError> {
    let rt = rt()?;
    let cli = client(&account)?;

    rt.block_on(async {
        let result = cli
            .sync_collection(calendar_url, sync_token.as_deref(), None, false)
            .await
            .map_err(|e| bridge_error("SyncCollection", e))?;

        Ok(SyncCollectionResult {
            sync_token: result.sync_token,
            items: result
                .items
                .into_iter()
                .map(|item| SyncCollectionItem {
                    href: item.href,
                    is_deleted: item.is_deleted,
                })
                .collect(),
        })
    })
}

/// Fetch a set of events by href using CalDAV `calendar-multiget`.
#[uniffi::export]
pub fn calendar_multiget(
    account: DavAccount,
    calendar_url: &str,
    hrefs: Vec<String>,
) -> Result<Vec<EventEntry>, CaldavError> {
    if hrefs.is_empty() {
        return Ok(Vec::new());
    }

    let rt = rt()?;
    let cli = client(&account)?;

    rt.block_on(async {
        let objects = cli
            .calendar_multiget(calendar_url, hrefs.iter().map(String::as_str), true)
            .await
            .map_err(|e| bridge_error("CalendarMultiGet", e))?;

        Ok(objects
            .into_iter()
            .filter_map(|obj| {
                let etag = obj.etag.unwrap_or_default();
                obj.calendar_data
                    .and_then(|data| parse_ics(obj.href, etag, data))
            })
            .collect())
    })
}

/// Create a new event. Returns the server-assigned URL + etag.
#[uniffi::export]
pub fn create_event(account: DavAccount, calendar_url: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let rt = rt()?;
    let cli = client(&account)?;
    let body = bytes::Bytes::from(ics_data.as_bytes().to_vec());

    rt.block_on(async {
        let uid = uuid::Uuid::new_v4();
        let path = format!("{}/{uid}.ics", calendar_url.trim_end_matches('/'));
        let resp = cli.put_if_none_match(&path, body)
            .await.map_err(|e| bridge_error("Create", e))?;
        ensure_success("Create", &resp)?;
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
pub fn update_event(account: DavAccount, event_url: &str, etag: &str, ics_data: &str) -> Result<MutateResult, CaldavError> {
    let rt = rt()?;
    let cli = client(&account)?;
    let body = bytes::Bytes::from(ics_data.as_bytes().to_vec());

    rt.block_on(async {
        let resp = cli.put_if_match(event_url, body, etag)
            .await.map_err(|e| bridge_error("Update", e))?;
        ensure_success("Update", &resp)?;
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
pub fn delete_event(account: DavAccount, event_url: &str, etag: &str) -> Result<(), CaldavError> {
    let rt = rt()?;
    let cli = client(&account)?;

    rt.block_on(async {
        let resp = cli.delete_if_match(event_url, etag).await.map_err(|e| bridge_error("Delete", e))?;
        ensure_success("Delete", &resp)
    })
}


