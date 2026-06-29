//! Error type exposed to Kotlin via UniFFI.

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CaldavError {
    #[error("{msg}")]
    Bridge { msg: String },
}

/// Build a [`CaldavError::Bridge`] with a context-prefixed message.
pub(crate) fn bridge_error(context: &str, e: impl std::fmt::Display) -> CaldavError {
    CaldavError::Bridge { msg: format!("{context}: {e}") }
}

