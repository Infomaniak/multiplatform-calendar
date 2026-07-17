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
    /// IANA TZID parameter of `DTSTART`, when present (RFC 5545 FORM #3). `None` if the value is a
    /// `DATE`, a UTC `DATE-TIME` (Z suffix) or floating (no `TZID`, no `Z`).
    pub dtstart_tzid: Option<String>,
    pub dtend: Option<String>,
    /// IANA TZID parameter of `DTEND`, when present. Same semantics as [`dtstart_tzid`].
    pub dtend_tzid: Option<String>,
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
    /// Raw `X-APPLE-CALENDAR-COLOR` value (typically `#RRGGBB` or `#RRGGBBAA`).
    pub color_hex: Option<String>,
    /// Raw RFC 7986 §5.9 `COLOR` value (a case-insensitive CSS3 color name).
    pub color_ical_name: Option<String>,
    pub attendees: Vec<AttendeeEntry>,
    pub alarms: Vec<AlarmEntry>,
}

#[derive(uniffi::Record)]
pub struct AlarmEntry {
    pub action: String,
    pub trigger_duration: Option<String>,
    pub trigger_absolute: Option<String>,
    pub trigger_related_to: String,
    pub description: Option<String>,
    pub summary: Option<String>,
    pub attendees: Vec<String>,
    pub attach: Vec<String>,
}

/// A single item returned by `sync-collection`.
#[derive(uniffi::Record)]
pub struct EventChangeRef {
    pub href: String,
    pub is_deleted: bool,
}

/// Incremental sync response containing the next token and changed/deleted resources.
#[derive(uniffi::Record)]
pub struct EventSyncDelta {
    pub sync_token: Option<String>,
    pub items: Vec<EventChangeRef>,
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

/// Minimal `VTIMEZONE` definition for a single IANA TZID referenced by an event.
///
/// We can't synthesize a full RFC 5545 `VTIMEZONE` (with every DST transition) in Rust without a
/// time-zone database, so the Kotlin side — which already has one (`kotlinx-datetime`) — provides the
/// UTC offset valid at the event's date. The Rust side emits a single static `STANDARD` sub-component
/// with `TZOFFSETFROM`/`TZOFFSETTO` set to this offset. This resolves the event's wall-clock correctly
/// in every client; it is only approximate for other dates of a DST zone, which is acceptable for a
/// per-event reference.
#[derive(uniffi::Record)]
pub struct VTimeZoneSpec {
    /// IANA TZID, e.g. "Europe/Paris".
    pub tzid: String,
    /// UTC offset in RFC 5545 `TZOFFSETTO` format, e.g. "+0200" or "-0500".
    pub offset: String,
}

/// Requested change to a VEVENT's color (RFC 7986 `COLOR` and/or Apple's `X-APPLE-CALENDAR-COLOR`).
#[derive(uniffi::Enum)]
pub enum ColorChange {
    /// Leave both properties as-is (no color emitted on create).
    Unchanged,
    /// Write `X-APPLE-CALENDAR-COLOR:<hex>` and drop any existing `COLOR:<name>`.
    Set { hex: String },
    Cleared,
}

/// Requested change to a VEVENT's VALARM sub-components.
/// `Unchanged` leaves source VALARM blocks untouched so `X-*` / exotic params survive partial edits.
#[derive(uniffi::Enum)]
pub enum AlarmsChange {
    Unchanged,
    Set { alarms: Vec<AlarmEdit> },
}

/// Edited event fields applied onto an existing VEVENT by `patch_event_ics`.
///
/// Date/date-time values are raw RFC 5545 strings:
/// - `all_day = true`           → `dtstart`/`dtend` are dates ("20260616"), no TZID.
/// - `dtstart_tzid = Some(id)`  → `dtstart` is local wall-clock ("20260616T100000"), serialized with `TZID=<id>`.
/// - otherwise                  → `dtstart` is UTC ("20260616T100000Z"), no TZID.
///
/// `timezones` carries a `VTIMEZONE` definition for every `TZID` referenced above, so the emitted
/// iCalendar object is self-contained (RFC 5545 §3.6.5). Empty for all-day/UTC/floating events.
///
/// `stamp` is the UTC `DTSTAMP`/`LAST-MODIFIED` value (the caller owns the clock).
#[derive(uniffi::Record)]
pub struct EventEdit {
    pub summary: Option<String>,
    pub dtstart: String,
    pub dtstart_tzid: Option<String>,
    pub dtend: Option<String>,
    pub dtend_tzid: Option<String>,
    pub all_day: bool,
    pub location: Option<String>,
    pub description: Option<String>,
    pub timezones: Vec<VTimeZoneSpec>,
    pub color_change: ColorChange,
    pub alarms_change: AlarmsChange,
    pub stamp: String,
}

/// Fresh VALARM fields emitted for [`AlarmsChange::Set`]; hand-emitted due to `icalendar::Alarm` limitations.
#[derive(uniffi::Record)]
pub struct AlarmEdit {
    pub action: String,
    pub trigger_duration: Option<String>,
    pub trigger_absolute: Option<String>,
    pub trigger_related_to: String,
    pub description: Option<String>,
    pub summary: Option<String>,
    pub attendees: Vec<String>,
    pub attach: Vec<String>,
}

