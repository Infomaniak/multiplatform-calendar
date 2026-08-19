//! Global CalDAV client configuration, plus a per-account client cache: building a client on every
//! operation threw away the TLS setup and the connection pool each time.
//!
//! Debug-interception options (proxy, extra trust anchors) are compiled in **only** under the
//! `debug-interception` Cargo feature. See `Cargo.toml` for why `cfg(debug_assertions)` cannot be
//! used.

use std::collections::HashMap;
use std::sync::{OnceLock, RwLock};
use std::time::Duration;

use fast_dav_rs::{CalDavClient, CalDavClientBuilder};

use crate::error::{bridge_error, CaldavError};
use crate::models::DavAccount;

/// Tunables applied to every CalDAV client built by the bridge. An all-default value reproduces the
/// library's own defaults (20 s request timeout, 32 idle connections per host, no `User-Agent`).
#[derive(uniffi::Record, Clone, Default)]
pub struct DavClientConfig {
    #[uniffi(default = None)]
    pub user_agent: Option<String>,
    #[uniffi(default = None)]
    pub timeout_ms: Option<u64>,
    #[uniffi(default = None)]
    pub connect_timeout_ms: Option<u64>,
    #[uniffi(default = None)]
    pub pool_max_idle_per_host: Option<u32>,
    #[uniffi(default = None)]
    pub pool_idle_timeout_ms: Option<u64>,

    // ---- Debug interception (ignored unless built with `debug-interception`) ----
    #[uniffi(default = None)]
    pub proxy_url: Option<String>,
    /// Extra PEM trust anchors. Needed on Android, where the system trust store ignores
    /// user-installed certificates.
    #[uniffi(default = [])]
    pub extra_root_certs_pem: Vec<Vec<u8>>,
}

/// The password is part of the key, so a credential change misses the cache instead of reusing a
/// client with a stale `Authorization`.
type ClientKey = (String, String, String);

#[derive(Default)]
struct State {
    config: DavClientConfig,
    clients: HashMap<ClientKey, CalDavClient>,
}

fn state() -> &'static RwLock<State> {
    static STATE: OnceLock<RwLock<State>> = OnceLock::new();
    STATE.get_or_init(|| RwLock::new(State::default()))
}

/// Call once at startup, before any CalDAV operation. Cached clients are dropped so the new settings
/// take effect immediately; in-flight requests keep running on their own client clone.
#[uniffi::export]
pub fn configure_dav_client(config: DavClientConfig) {
    let mut state = state().write().unwrap_or_else(|e| e.into_inner());
    state.config = config;
    state.clients.clear();
}

/// Drop every cached client for `base_url`/`username`, whatever password it was built with.
///
/// Call when credentials are removed or replaced: entries are keyed by password, so a rotation would
/// otherwise leave the previous client — and the previous password — resident until process exit.
#[uniffi::export]
pub fn evict_dav_clients(base_url: String, username: String) {
    let mut state = state().write().unwrap_or_else(|e| e.into_inner());
    state
        .clients
        .retain(|(url, user, _), _| url != &base_url || user != &username);
}

/// Return a client for `account`, building and caching it on first use.
pub(crate) fn cached_client(account: &DavAccount) -> Result<CalDavClient, CaldavError> {
    let key = (
        account.base_url.clone(),
        account.username.clone(),
        account.password.clone(),
    );

    // The braces are load-bearing: they drop the read guard before the write lock is taken below.
    {
        let state = state().read().unwrap_or_else(|e| e.into_inner());
        if let Some(client) = state.clients.get(&key) {
            return Ok(client.clone());
        }
    }

    let mut state = state().write().unwrap_or_else(|e| e.into_inner());
    // Another thread may have inserted between the read and the write lock.
    if let Some(client) = state.clients.get(&key) {
        return Ok(client.clone());
    }

    let client = build_client(account, &state.config)?;
    state.clients.insert(key, client.clone());
    Ok(client)
}

fn build_client(
    account: &DavAccount,
    config: &DavClientConfig,
) -> Result<CalDavClient, CaldavError> {
    let mut builder = CalDavClient::builder(&account.base_url)
        .basic_auth(&account.username, &account.password);

    if let Some(user_agent) = &config.user_agent {
        builder = builder.user_agent(user_agent);
    }
    if let Some(ms) = config.timeout_ms {
        builder = builder.timeout(Duration::from_millis(ms));
    }
    if let Some(ms) = config.connect_timeout_ms {
        builder = builder.connect_timeout(Duration::from_millis(ms));
    }
    if let Some(max_idle) = config.pool_max_idle_per_host {
        builder = builder.pool_max_idle_per_host(max_idle as usize);
    }
    if let Some(ms) = config.pool_idle_timeout_ms {
        builder = builder.pool_idle_timeout(Duration::from_millis(ms));
    }

    builder = apply_debug_interception(builder, config)?;

    builder.build().map_err(|e| bridge_error("Client", e))
}

#[cfg(feature = "debug-interception")]
fn apply_debug_interception(
    mut builder: CalDavClientBuilder,
    config: &DavClientConfig,
) -> Result<CalDavClientBuilder, CaldavError> {
    use http::Uri;

    if let Some(proxy_url) = &config.proxy_url {
        let uri: Uri = proxy_url
            .parse()
            .map_err(|e| bridge_error("ProxyUri", e))?;
        builder = builder.proxy(uri);
    }

    if !config.extra_root_certs_pem.is_empty() {
        builder = builder.extra_root_certs_pem(config.extra_root_certs_pem.clone());
    }

    Ok(builder)
}

#[cfg(not(feature = "debug-interception"))]
fn apply_debug_interception(
    builder: CalDavClientBuilder,
    _config: &DavClientConfig,
) -> Result<CalDavClientBuilder, CaldavError> {
    Ok(builder)
}
