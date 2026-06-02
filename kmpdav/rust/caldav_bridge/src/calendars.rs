//! Calendar discovery operations.

use crate::client::{client, rt};
use crate::error::{err, CaldavError};
use crate::models::CalendarEntry;

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
            for cal in cli.list_calendars(home).await.map_err(|e| err("ListCalendars", e))? {
                calendars.push(CalendarEntry {
                    url: cal.href.clone(),
                    display_name: cal.displayname.unwrap_or_else(|| {
                        cal.href.trim_end_matches('/').rsplit('/').next()
                            .unwrap_or(&cal.href).to_string()
                    }),
                    color: cal.color.clone(),
                    description: cal.description.clone(),
                    ctag: cal.sync_token.clone(),
                });
            }
        }
        Ok(calendars)
    })
}

