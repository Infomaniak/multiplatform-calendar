//! UniFFI records exposed to Kotlin as data classes.

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
}

/// Reference to a created/updated event on the server (URL + etag).
#[derive(uniffi::Record)]
pub struct MutateResult {
    pub url: String,
    pub etag: String,
}

