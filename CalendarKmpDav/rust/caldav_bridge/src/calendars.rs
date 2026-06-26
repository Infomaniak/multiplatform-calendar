//! Calendar discovery operations.

use crate::client::{client, rt};
use crate::error::{err, CaldavError};
use crate::models::{CalendarAccessLevel, CalendarEntry};
use crate::props::{access_level, collection_props, normalize_href};

/// Discover all calendars for the given credentials.
#[uniffi::export]
pub fn discover(base_url: &str, username: &str, password: &str) -> Result<Vec<CalendarEntry>, CaldavError> {
    let rt = rt()?;
    let cli = client(base_url, username, password)?;

    rt.block_on(async {
        let principal = cli.discover_current_user_principal().await
            .map_err(|e| err("Principal", e))?
            .ok_or_else(|| CaldavError::Bridge { msg: "No current-user-principal".into() })?;

        let homes = cli.discover_calendar_home_set(&principal).await
            .map_err(|e| err("HomeSet", e))?;

        let mut calendars = Vec::new();
        for home in &homes {
            let props = collection_props(&cli, home).await;
            for cal in cli.list_calendars(home).await.map_err(|e| err("ListCalendars", e))? {
                let entry_props = props.get(normalize_href(&cal.href).as_str());
                let access_level = entry_props
                    .map(access_level)
                    .unwrap_or(CalendarAccessLevel::ReadWrite);
                // Prefer the color from our PROPFIND, fall back to the listing's.
                let color = entry_props
                    .and_then(|p| p.color.clone())
                    .or_else(|| cal.color.clone());
                calendars.push(CalendarEntry {
                    url: cal.href.clone(),
                    display_name: cal.displayname.unwrap_or_else(|| {
                        cal.href.trim_end_matches('/').rsplit('/').next()
                            .unwrap_or(&cal.href).to_string()
                    }),
                    color,
                    description: cal.description.clone(),
                    ctag: cal.sync_token.clone(),
                    access_level,
                });
            }
        }
        Ok(calendars)
    })
}

