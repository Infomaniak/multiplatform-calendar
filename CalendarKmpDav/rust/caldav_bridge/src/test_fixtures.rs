//! Test-only exports. Included unconditionally in the compiled bridge so
//! Kotlin tests can call them without a dedicated build flavour or feature
//! flag. Not intended for production use.

use std::future::pending;
use std::sync::atomic::{AtomicU32, Ordering};

use crate::error::CaldavError;

static STARTED: AtomicU32 = AtomicU32::new(0);
static DROPPED: AtomicU32 = AtomicU32::new(0);

/// RAII guard that increments [`DROPPED`] when the enclosing future is dropped,
/// whether via normal completion or via cancellation.
struct DropCounter;

impl Drop for DropCounter {
    fn drop(&mut self) {
        DROPPED.fetch_add(1, Ordering::SeqCst);
    }
}

/// Suspend forever inside the Tokio runtime. Test hook that proves cancelling
/// the calling Kotlin coroutine actually drops the Rust future (the future
/// never completes on its own — if cancel does not propagate, the drop
/// counter stays at zero and the corresponding assertion fails).
///
/// Observable state is exposed via [`test_await_forever_started_count`] and
/// [`test_await_forever_dropped_count`]. Callers should invoke
/// [`reset_test_await_forever_counters`] at the start of each test to avoid
/// pollution from previous cases (counters are process-global).
#[uniffi::export(async_runtime = "tokio")]
pub async fn test_await_forever() -> Result<(), CaldavError> {
    let _guard = DropCounter;
    STARTED.fetch_add(1, Ordering::SeqCst);
    pending::<()>().await;
    Ok(())
}

/// Number of times [`test_await_forever`] began polling since the last call to
/// [`reset_test_await_forever_counters`].
#[uniffi::export]
pub fn test_await_forever_started_count() -> u32 {
    STARTED.load(Ordering::SeqCst)
}

/// Number of times the future returned by [`test_await_forever`] was dropped
/// since the last call to [`reset_test_await_forever_counters`].
#[uniffi::export]
pub fn test_await_forever_dropped_count() -> u32 {
    DROPPED.load(Ordering::SeqCst)
}

/// Reset both counters to zero.
#[uniffi::export]
pub fn reset_test_await_forever_counters() {
    STARTED.store(0, Ordering::SeqCst);
    DROPPED.store(0, Ordering::SeqCst);
}
