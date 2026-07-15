// caldav_bridge – Thin UniFFI wrapper around fast-dav-rs for KMP.
//
// Crate layout (flat — one module per file):
// - error      → CaldavError + err helper
// - client     → internal CalDAV client builder (auth, response checks)
// - models     → UniFFI records (Kotlin data classes)
// - props      → extra CalDAV collection props (privileges, color) via PROPFIND
// - calendars  → calendar discovery
// - events     → event fetch/create/update/delete + iCalendar parsing
//
// Async & cancellation
// --------------------
// All network exports are `async fn` annotated with
// `#[uniffi::export(async_runtime = "tokio")]`. UniFFI generates Kotlin
// `suspend fun` bindings and drives the async work on its own Tokio runtime;
// cancelling the calling coroutine drops the Rust future, which drops the
// underlying reqwest request. Reads (discover/fetch/query/sync) are fully
// cancellable. Writes (update-calendar/create-event/update-event/delete-event)
// are best-effort: cancellation after the request is dispatched leaves the
// server-side outcome undefined, and callers must re-sync to reconcile.
//
// Pure-CPU exports (`patch_event_ics`, `build_event_ics`) stay synchronous.

uniffi::setup_scaffolding!();

mod alarms;
mod client;
mod error;
mod ical_components;
mod models;
mod props;

mod calendars;
mod events;

mod test_fixtures;
