//! VALARM parsing.

use icalendar::Component;

use crate::ical_components::{ATTENDEE, DESCRIPTION, SUMMARY, VALARM, VALUE_PARAM};
use crate::models::AlarmEntry;

const ACTION: &str = "ACTION";
const TRIGGER: &str = "TRIGGER";
const ATTACH: &str = "ATTACH";
const RELATED_PARAM: &str = "RELATED";
const DATE_TIME_VALUE: &str = "DATE-TIME";
const RELATED_START: &str = "START";
const DEFAULT_ACTION: &str = "DISPLAY";

pub(crate) fn parse_alarms(ev: &icalendar::Event) -> Vec<AlarmEntry> {
    ev.components()
        .iter()
        .filter(|c| c.component_kind().eq_ignore_ascii_case(VALARM))
        .filter_map(parse_valarm)
        .collect()
}

/// VALARMs without a TRIGGER are malformed and dropped.
fn parse_valarm<C: Component>(c: &C) -> Option<AlarmEntry> {
    let trigger = c.properties().get(TRIGGER)?;
    let related = trigger.params().get(RELATED_PARAM)
        .map(|p| p.value().to_ascii_uppercase())
        .unwrap_or_else(|| RELATED_START.to_string());
    let is_absolute = trigger.params().get(VALUE_PARAM)
        .map(|p| p.value().eq_ignore_ascii_case(DATE_TIME_VALUE))
        .unwrap_or_else(|| trigger.value().ends_with('Z'));
    let (trigger_duration, trigger_absolute) = if is_absolute {
        (None, Some(trigger.value().to_string()))
    } else {
        (Some(trigger.value().to_string()), None)
    };
    let attendees = c.multi_properties()
        .get(ATTENDEE)
        .map(|list| list.iter().map(|p| p.value().to_string()).collect())
        .unwrap_or_default();
    Some(AlarmEntry {
        action: c.properties().get(ACTION)
            .map(|p| p.value().to_ascii_uppercase())
            .unwrap_or_else(|| DEFAULT_ACTION.to_string()),
        trigger_duration,
        trigger_absolute,
        trigger_related_to: related,
        description: c.properties().get(DESCRIPTION).map(|p| p.value().to_string()),
        summary: c.properties().get(SUMMARY).map(|p| p.value().to_string()),
        attendees,
        attach: c.properties().get(ATTACH).map(|p| p.value().to_string()),
    })
}
