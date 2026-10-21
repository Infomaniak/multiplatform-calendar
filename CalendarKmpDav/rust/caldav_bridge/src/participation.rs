//! Replies to an invitation: the user's own `PARTSTAT`, all an attendee may change (RFC 6638 §3.2.2.1).

use icalendar::{Calendar, CalendarComponent, Component, Property};

use crate::attendees::{has_email, with_param};
use crate::error::{bridge_error, CaldavError};
use crate::events::{
    master_vevent,
    override_seeded_from,
    override_vevent_index,
    recurrence_id_property,
    refresh_stamp,
    reparse_edited,
    set_bounds,
};
use crate::ical_components::ATTENDEE;
use crate::models::{EventEntry, OccurrenceSlot, RecurrenceIdSpec, ReplyScope};

const CONTEXT: &str = "Reply";
const PARTSTAT_PARAM: &str = "PARTSTAT";

/// Set to `partstat` the `PARTSTAT` of the `ATTENDEE` line matching one of `emails`, in the VEVENTs `scope` designates.
///
/// `SEQUENCE` is left alone, being the organizer's to bump (RFC 5546 §2.1.4); a detached instance takes its master's.
/// Fails when none of those VEVENTs lists the user.
#[uniffi::export]
pub fn set_participation_status(
    ics_data: &str,
    scope: ReplyScope,
    emails: Vec<String>,
    partstat: String,
    stamp: String,
) -> Result<EventEntry, CaldavError> {
    let mut calendar: Calendar = ics_data.parse().map_err(|e| bridge_error(CONTEXT, e))?;
    let reply = Reply { emails: &emails, partstat: &partstat, stamp: &stamp };

    let replied = match scope {
        ReplyScope::Series => calendar
            .components
            .iter_mut()
            .filter_map(as_vevent)
            .fold(false, |replied, event| reply.write_to(event) || replied),
        ReplyScope::Occurrence { recurrence_id, slot } => match override_vevent_index(&calendar, &recurrence_id) {
            Some(index) => as_vevent(&mut calendar.components[index]).is_some_and(|event| reply.write_to(event)),
            None => {
                let master = master_vevent(&calendar).ok_or_else(|| bridge_error(CONTEXT, "no master VEVENT in ICS"))?;
                let mut event = detached(master, &recurrence_id, &slot);
                let replied = reply.write_to(&mut event);
                if replied {
                    calendar.push(event);
                }
                replied
            }
        },
    };

    if !replied {
        return Err(bridge_error(CONTEXT, "the user is not an attendee"));
    }
    reparse_edited(calendar.to_string(), CONTEXT)
}

struct Reply<'a> {
    emails: &'a [String],
    partstat: &'a str,
    stamp: &'a str,
}

impl Reply<'_> {
    /// `false`, leaving `event` untouched, when it does not list the user.
    fn write_to(&self, event: &mut icalendar::Event) -> bool {
        let Some(lines) = event.multi_properties().get(ATTENDEE).cloned() else { return false };
        if !lines.iter().any(|line| self.is_user(line)) {
            return false;
        }

        event.remove_multi_property(ATTENDEE);
        for line in lines {
            let line = if self.is_user(&line) { with_param(&line, PARTSTAT_PARAM, Some(self.partstat)) } else { line };
            event.append_multi_property(line);
        }
        refresh_stamp(event, self.stamp);
        true
    }

    fn is_user(&self, line: &Property) -> bool {
        self.emails.iter().any(|email| has_email(line, email))
    }
}

/// The override `recurrence_id` designates, seeded from `master` and shown on `slot`.
fn detached(master: &icalendar::Event, recurrence_id: &RecurrenceIdSpec, slot: &OccurrenceSlot) -> icalendar::Event {
    let mut event = override_seeded_from(master, true);
    if let Some(sequence) = master.property_value("SEQUENCE") {
        event.add_property("SEQUENCE", sequence);
    }
    event.append_property(recurrence_id_property(recurrence_id));
    set_bounds(&mut event, slot);
    event
}

fn as_vevent(component: &mut CalendarComponent) -> Option<&mut icalendar::Event> {
    match component {
        CalendarComponent::Event(event) => Some(event),
        _ => None,
    }
}
