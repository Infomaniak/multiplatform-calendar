// caldav_bridge – Thin UniFFI wrapper around fast-dav-rs for KMP.
//
// Crate layout (flat — one module per file):
// - error     → CaldavError + err helper
// - client    → internal Tokio runtime + CalDAV client builders
// - models    → UniFFI records (Kotlin data classes)
// - calendars → calendar discovery
// - events    → event fetch/create/update/delete + iCalendar parsing

uniffi::setup_scaffolding!();

mod client;
mod error;
mod models;

mod calendars;
mod events;
