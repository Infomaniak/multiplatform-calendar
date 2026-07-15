//! VALARM parsing/emission.
//!
//! We hand-splice VALARM blocks because `icalendar` cannot replace sub-components or emit EMAIL/unknown alarms.

use icalendar::{Component, Property};

use crate::ical_components::{
    is_begin_marker, is_end_marker, push_begin, push_end, ATTENDEE, DESCRIPTION, SUMMARY, VALARM,
    VALUE_PARAM, VEVENT,
};
use crate::models::{AlarmEdit, AlarmEntry};

const ACTION: &str = "ACTION";
const TRIGGER: &str = "TRIGGER";
const ATTACH: &str = "ATTACH";
const RELATED_PARAM: &str = "RELATED";
const DATE_TIME_VALUE: &str = "DATE-TIME";
const RELATED_START: &str = "START";
const RELATED_END: &str = "END";
const DEFAULT_ACTION: &str = "DISPLAY";

pub(crate) fn parse_alarms(ev: &icalendar::Event) -> Vec<AlarmEntry> {
    ev.components()
        .iter()
        .filter(|c| c.component_kind().eq_ignore_ascii_case(VALARM))
        .filter_map(parse_valarm)
        .collect()
}

/// Strips VALARMs from the first VEVENT only, preserving recurrence-exception alarms.
/// Only the line terminator is trimmed so folded continuation lines aren't mistaken for boundaries.
pub(crate) fn strip_valarms_in_first_vevent(ics: &str) -> String {
    let mut out = String::with_capacity(ics.len());
    let mut in_vevent = false;
    let mut done = false;
    let mut in_alarm = false;
    for line in ics.split_inclusive('\n') {
        let marker = line.trim_end_matches(['\r', '\n']);
        if !done && !in_vevent && is_begin_marker(marker, VEVENT) {
            in_vevent = true;
            out.push_str(line);
            continue;
        }
        if in_vevent {
            if is_begin_marker(marker, VALARM) {
                in_alarm = true;
                continue;
            }
            if in_alarm {
                if is_end_marker(marker, VALARM) {
                    in_alarm = false;
                }
                continue;
            }
            if is_end_marker(marker, VEVENT) {
                in_vevent = false;
                done = true;
            }
        }
        out.push_str(line);
    }
    out
}

/// Splices VALARM blocks before the first VEVENT's END:VEVENT, matched as a complete content line.
pub(crate) fn splice_alarms_into_first_vevent(ics: &str, alarms: &[AlarmEdit]) -> String {
    if alarms.is_empty() {
        return ics.to_string();
    }
    let blocks: String = alarms.iter().filter_map(build_alarm_block).collect();
    if blocks.is_empty() {
        return ics.to_string();
    }
    let mut out = String::with_capacity(ics.len() + blocks.len());
    let mut in_vevent = false;
    let mut done = false;
    for line in ics.split_inclusive('\n') {
        let marker = line.trim_end_matches(['\r', '\n']);
        if !done && !in_vevent && is_begin_marker(marker, VEVENT) {
            in_vevent = true;
        } else if in_vevent && is_end_marker(marker, VEVENT) {
            out.push_str(&blocks);
            in_vevent = false;
            done = true;
        }
        out.push_str(line);
    }
    out
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
    // RFC 5545 allows several ATTACH properties on an EMAIL VALARM, so collect them all.
    let attach = c.multi_properties()
        .get(ATTACH)
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
        attach,
    })
}

fn build_alarm_block(a: &AlarmEdit) -> Option<String> {
    let mut trigger = if let Some(dur) = &a.trigger_duration {
        let mut p = Property::new(TRIGGER, dur);
        if a.trigger_related_to.eq_ignore_ascii_case(RELATED_END) {
            p.add_parameter(RELATED_PARAM, RELATED_END);
        }
        p
    } else if let Some(at) = &a.trigger_absolute {
        let mut p = Property::new(TRIGGER, at);
        p.add_parameter(VALUE_PARAM, DATE_TIME_VALUE);
        p
    } else {
        return None;
    };

    let mut s = String::new();
    push_begin(&mut s, VALARM);
    s.push_str(&property_line(Property::new(ACTION, &a.action)));
    s.push_str(&property_line(trigger.done()));
    if let Some(d) = &a.description {
        s.push_str(&property_line(Property::new(DESCRIPTION, d)));
    }
    if let Some(sm) = &a.summary {
        s.push_str(&property_line(Property::new(SUMMARY, sm)));
    }
    for att in &a.attendees {
        s.push_str(&property_line(Property::new(ATTENDEE, att)));
    }
    for att in &a.attach {
        s.push_str(&property_line(Property::new(ATTACH, att)));
    }
    push_end(&mut s, VALARM);
    Some(s)
}

fn property_line(prop: Property) -> String {
    prop.try_into().unwrap_or_default()
}
