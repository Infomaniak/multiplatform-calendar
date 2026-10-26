//! Pull-based streaming bridge: each UniFFI call advances the HTTP response by one bounded batch.

use std::sync::Arc;

use fast_dav_rs::{caldav::CalendarObject, webdav::streaming::ItemStream};
use futures_util::StreamExt;
use tokio::sync::Mutex;

use crate::client::client;
use crate::error::{map_fast_dav_error, CaldavError};
use crate::events::parse_ics;
use crate::models::{DavAccount, EventEntry};

const MAX_BATCH_SIZE: usize = 32;
const MAX_BATCH_ICS_BYTES: usize = 512 * 1024;

struct StreamState {
    stream: ItemStream<CalendarObject>,
    pending: Option<EventEntry>,
}

/// An open CalDAV REPORT response. Dropping this object aborts the in-flight HTTP download.
///
/// The stream is consumed only when `next_batch` is called; no worker accumulates events in the
/// background. A mutex prevents concurrent callers from advancing the same response. The Kotlin
/// caller must `closeStream` in a `finally` block, including after coroutine cancellation.
#[derive(uniffi::Object)]
pub struct CalendarRangeStream {
    inner:Mutex<Option<StreamState>>,
}

#[uniffi::export(async_runtime = "tokio")]
impl CalendarRangeStream {
    /// Return up to 32 successfully parsed events, capped at 512 KiB of raw ICS data.
    ///
    /// The event that would exceed the batch budget is kept for the next call.
    /// An individual event exceeding 512 KiB is returned alone, without truncation.
    ///
    /// Returns `None` on EOF or after `close_stream`.
    /// Server/parser errors are propagated rather than being mistaken for EOF.
    /// Individual ICS resources that cannot be interpreted as VEVENTs are skipped.
    pub async fn next_batch(&self) -> Result<Option<Vec<EventEntry>>, CaldavError> {
        let mut guard = self.inner.lock().await;

        let Some(state) = guard.as_mut() else {
            return Ok(None);
        };

        let mut events = Vec::with_capacity(MAX_BATCH_SIZE);
        let mut total_ics_bytes = 0usize;

        while events.len() < MAX_BATCH_SIZE {
            // Consume the pending event first, if the previous batch reached
            // its memory budget before including it.
            let next_event = if let Some(event) = state.pending.take() {
                Some(event)
            } else {
                match state.stream.next().await {
                    Some(Ok(obj)) => {
                        let etag = obj.etag.unwrap_or_default();
                        obj.calendar_data
                            .and_then(|data| parse_ics(obj.href, etag, data))
                    }
                    Some(Err(error)) => {
                        // A failed batch must never be returned as if the response
                        // had completed successfully.
                        *guard = None;
                        return Err(map_fast_dav_error("CalendarQueryStream", error));
                    }
                    None => {
                        // The stream has reached EOF.
                        *guard = None;
                        return if events.is_empty() { Ok(None) } else { Ok(Some(events)) };
                    }
                }
            };

            let Some(event) = next_event else {
                // Skip invalid/non-VEVENT resources.
                continue;
            };

            let event_ics_bytes = event.ics_data.len();

            // Avoid exceeding the memory budget, except when a single event
            // already exceeds it: that event must be returned alone.
            if !events.is_empty() && would_exceed_ics_budget(total_ics_bytes, event_ics_bytes) {
                state.pending = Some(event);
                break;
            }

            total_ics_bytes = total_ics_bytes.saturating_add(event_ics_bytes);
            events.push(event);

            // Stop as soon as either limit is reached.
            if total_ics_bytes >= MAX_BATCH_ICS_BYTES {
                break;
            }
        }

        Ok(Some(events))
    }

    /// Abort the response without waiting to read the rest of the XML document.
    pub async fn close_stream(&self) {
        self.inner.lock().await.take();
    }
}

/// Open a CalDAV time-range query without collecting the returned objects in memory.
#[uniffi::export(async_runtime = "tokio")]
pub async fn open_calendar_range_stream(
    account: DavAccount,
    calendar_url: &str,
    start: &str,
    end: &str,
) -> Result<Arc<CalendarRangeStream>, CaldavError> {
    let stream = client(&account)?
        .calendar_query_stream(calendar_url, "VEVENT", Some(start), Some(end), true, None)
        .await
        .map_err(|error| map_fast_dav_error("CalendarQueryStream", error))?;

    Ok(Arc::new(CalendarRangeStream {
        inner: Mutex::new(Some(StreamState {
            stream,
            pending: None,
        })),
    }))
}

fn would_exceed_ics_budget(current_bytes: usize, next_bytes: usize) -> bool {
    current_bytes.saturating_add(next_bytes) > MAX_BATCH_ICS_BYTES
}
