//! VALARM parsing/emission.
//!
//! We hand-splice VALARM blocks because `icalendar` cannot replace sub-components or emit EMAIL/unknown alarms.

use icalendar::{Component, Property};

use crate::ical_components::{
    is_begin_marker, is_end_marker, push_begin, push_end, ATTENDEE, DESCRIPTION, SUMMARY, VALARM,
    VALUE_PARAM, VEVENT,
};
use crate::models::{AlarmEdit, AlarmEntry, AlarmRepetitionSpec};

const UID: &str = "UID";
const ACTION: &str = "ACTION";
const TRIGGER: &str = "TRIGGER";
const ATTACH: &str = "ATTACH";
const REPEAT: &str = "REPEAT";
const DURATION: &str = "DURATION";
const RELATED_PARAM: &str = "RELATED";
const DATE_TIME_VALUE: &str = "DATE-TIME";
const RELATED_START: &str = "START";
const RELATED_END: &str = "END";
const DEFAULT_ACTION: &str = "DISPLAY";
/// The actions an edit governs; a VALARM with any other is kept verbatim through every edit.
const EDITABLE_ACTIONS: [&str; 3] = ["DISPLAY", "AUDIO", "EMAIL"];

/// A VALARM without `ACTION` reads as [`DEFAULT_ACTION`], so it is editable.
fn is_editable_action(action: Option<&str>) -> bool {
    action.map_or(true, |action| EDITABLE_ACTIONS.iter().any(|editable| action.eq_ignore_ascii_case(editable)))
}

/// Whether every edit keeps this VALARM, see [`EDITABLE_ACTIONS`].
pub(crate) fn is_uneditable_valarm<C: Component>(c: &C) -> bool {
    c.component_kind().eq_ignore_ascii_case(VALARM)
        && !is_editable_action(c.properties().get(ACTION).map(|p| p.value()))
}

pub(crate) fn parse_alarms(ev: &icalendar::Event) -> Vec<AlarmEntry> {
    ev.components()
        .iter()
        .filter(|c| c.component_kind().eq_ignore_ascii_case(VALARM))
        .filter_map(parse_valarm)
        .collect()
}

/// Strips the editable VALARMs (see [`EDITABLE_ACTIONS`]) from the VEVENT at `target_vevent` (0-based
/// ordinal among VEVENTs), leaving every other VEVENT's alarms — e.g. recurrence-exception overrides — untouched.
/// Only the line terminator is trimmed so folded continuation lines aren't mistaken for boundaries.
pub(crate) fn strip_editable_valarms_in_vevent(ics: &str, target_vevent: usize) -> String {
    let mut out = String::with_capacity(ics.len());
    let mut vevent_seen = 0usize;
    let mut in_target = false;
    let mut done = false;
    let mut alarm: Option<String> = None;
    for line in ics.split_inclusive('\n') {
        let marker = line.trim_end_matches(['\r', '\n']);
        if !done && !in_target && is_begin_marker(marker, VEVENT) {
            let is_target = vevent_seen == target_vevent;
            vevent_seen += 1;
            if is_target {
                in_target = true;
            }
            out.push_str(line);
            continue;
        }
        if in_target {
            if is_begin_marker(marker, VALARM) {
                alarm = Some(line.to_string());
                continue;
            }
            if let Some(block) = alarm.as_mut() {
                block.push_str(line);
                if is_end_marker(marker, VALARM) {
                    if let Some(block) = alarm.take() {
                        if !is_editable_action(block_action(&block).as_deref()) {
                            out.push_str(&block);
                        }
                    }
                }
                continue;
            }
            if is_end_marker(marker, VEVENT) {
                in_target = false;
                done = true;
            }
        }
        out.push_str(line);
    }
    out
}

/// The `ACTION` of a VALARM block, read once its folded lines are joined back.
fn block_action(block: &str) -> Option<String> {
    let unfolded = block.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "").replace("\n\t", "");
    unfolded.lines().find_map(action_value)
}

/// The value of an `ACTION` content line, `None` for any other line.
fn action_value(line: &str) -> Option<String> {
    let name_end = line.find([';', ':'])?;
    if !line[..name_end].eq_ignore_ascii_case(ACTION) {
        return None;
    }
    let mut in_quotes = false;
    let value_start = line[name_end..].char_indices().find_map(|(i, c)| match c {
        '"' => {
            in_quotes = !in_quotes;
            None
        }
        ':' if !in_quotes => Some(name_end + i + 1),
        _ => None,
    })?;
    Some(line[value_start..].trim().to_string())
}

/// Splices VALARM blocks before the `END:VEVENT` of the VEVENT at `target_vevent` (0-based ordinal
/// among VEVENTs), matched as a complete content line.
pub(crate) fn splice_alarms_into_vevent(
    ics: &str,
    target_vevent: usize,
    alarms: &[AlarmEdit],
) -> String {
    if alarms.is_empty() {
        return ics.to_string();
    }
    let blocks: String = alarms.iter().filter_map(build_alarm_block).collect();
    if blocks.is_empty() {
        return ics.to_string();
    }
    let mut out = String::with_capacity(ics.len() + blocks.len());
    let mut vevent_seen = 0usize;
    let mut in_target = false;
    let mut done = false;
    for line in ics.split_inclusive('\n') {
        let marker = line.trim_end_matches(['\r', '\n']);
        if !done && !in_target && is_begin_marker(marker, VEVENT) {
            let is_target = vevent_seen == target_vevent;
            vevent_seen += 1;
            if is_target {
                in_target = true;
            }
        } else if in_target && is_end_marker(marker, VEVENT) {
            out.push_str(&blocks);
            in_target = false;
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
        uid: c.properties().get(UID).map(|p| p.value().to_string()),
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
        repetition: parse_repetition(c),
    })
}

/// `None` unless both `REPEAT` and `DURATION` are there, as RFC 5545 requires them together.
fn parse_repetition<C: Component>(c: &C) -> Option<AlarmRepetitionSpec> {
    let count = c.properties().get(REPEAT)?.value().trim().parse().ok()?;
    let interval = c.properties().get(DURATION)?.value().trim().to_string();
    Some(AlarmRepetitionSpec { count, interval })
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
    // `AlarmsChange::Set` rewrites the whole block, so the UID is written back or it is lost.
    if let Some(uid) = &a.uid {
        s.push_str(&property_line(Property::new(UID, uid)));
    }
    s.push_str(&property_line(Property::new(ACTION, &a.action)));
    s.push_str(&property_line(trigger.done()));
    if let Some(r) = &a.repetition {
        s.push_str(&property_line(Property::new(DURATION, &r.interval)));
        s.push_str(&property_line(Property::new(REPEAT, &r.count.to_string())));
    }
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
