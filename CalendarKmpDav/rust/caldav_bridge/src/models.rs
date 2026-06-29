//! UniFFI records exposed to Kotlin as data classes.

/// CalDAV credentials passed across the FFI boundary.
#[derive(uniffi::Record)]
pub struct DavAccount {
    pub base_url: String,
    pub username: String,
    pub password: String,
}

/// Access level the current user holds on a calendar collection.
///
/// Derived solely from the CalDAV `current-user-privilege-set` (RFC 3744).
#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq)]
pub enum CalendarAccessLevel {
    /// No privilege granted on the collection.
    None,
    /// Read-only access (`DAV:read`).
    Read,
    /// Read and write access (`DAV:write` / write-content / bind / unbind).
    ReadWrite,
    /// Full control: holds `DAV:all` or `DAV:write-acl`.
    Owner,
}

/// Calendar collection record returned by discovery.
#[derive(uniffi::Record)]
pub struct CalendarEntry {
    pub url: String,
    pub display_name: String,
    pub color: Option<String>,
    pub description: Option<String>,
    pub ctag: Option<String>,
    pub access_level: CalendarAccessLevel,
}

/// Event resource with parsed iCalendar fields.
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
    /// Raw RFC 5545 `DURATION` value (e.g. "PT1H"). Mutually exclusive with `dtend`.
    pub duration: Option<String>,
    pub created: Option<String>,
    pub last_modified: Option<String>,
    pub dtstamp: Option<String>,
    pub rrule: Option<String>,
    pub status: Option<String>,
    pub transp: Option<String>,
    pub classification: Option<String>,
    pub priority: Option<String>,
    pub sequence: Option<String>,
    pub categories: Option<String>,
    pub organizer: Option<String>,
    pub attendees: Vec<AttendeeEntry>,
}

/// A single ATTENDEE/ORGANIZER participant parsed from a VEVENT. Raw iCal parameter values
/// (PARTSTAT/ROLE) are kept verbatim and mapped to domain enums Kotlin-side.
#[derive(uniffi::Record)]
pub struct AttendeeEntry {
    pub email: String,
    pub display_name: Option<String>,
    /// Raw `PARTSTAT` (e.g. "ACCEPTED", "NEEDS-ACTION").
    pub status: Option<String>,
    /// Raw `ROLE` (e.g. "REQ-PARTICIPANT", "OPT-PARTICIPANT").
    pub role: Option<String>,
    pub is_organizer: bool,
    /// `RSVP=TRUE`: a response is expected from this attendee.
    pub response_needed: bool,
}

/// Reference to a created/updated event on the server (URL + etag).
#[derive(uniffi::Record)]
pub struct MutateResult {
    pub url: String,
    pub etag: String,
}

/// Edited calendar properties applied onto a CalDAV collection by `update_calendar` (PROPPATCH).
///
/// Each `Some` is set on the server; each `None` is left untouched. Colors are CalDAV hex strings
/// (`#RRGGBB` or `#RRGGBBAA`).
#[derive(uniffi::Record)]
pub struct CalendarEdit {
    pub display_name: Option<String>,
    pub color: Option<String>,
}

/// Edited event fields applied onto an existing VEVENT by `patch_event_ics`.
///
/// Date/date-time values are raw RFC 5545 strings (e.g. "20260616T100000Z" or "20260616" when
/// `all_day`). `stamp` is the UTC `DTSTAMP`/`LAST-MODIFIED` value (the caller owns the clock).
#[derive(uniffi::Record)]
pub struct EventEdit {
    pub summary: Option<String>,
    pub dtstart: String,
    pub dtend: Option<String>,
    pub all_day: bool,
    pub location: Option<String>,
    pub description: Option<String>,
    pub stamp: String,
}

