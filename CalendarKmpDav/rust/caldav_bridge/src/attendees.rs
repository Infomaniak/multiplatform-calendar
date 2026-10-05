//! ATTENDEE/ORGANIZER editing. Lines are matched on their email so the parameters we do not model survive.

use icalendar::{Component, Property};

use crate::events::strip_mailto;
use crate::ical_components::ATTENDEE;
use crate::models::{AttendeeEdit, AttendeesChange, NameChange, OrganizerChange};

const ORGANIZER: &str = "ORGANIZER";
const CN_PARAM: &str = "CN";
const ROLE_PARAM: &str = "ROLE";

pub(crate) fn apply_attendees_change(event: &mut icalendar::Event, change: &AttendeesChange) {
    let AttendeesChange::Set { attendees } = change else { return };

    let stored = event.multi_properties().get(ATTENDEE).cloned().unwrap_or_default();
    let stored_line = |email: &str| stored.iter().find(|line| has_email(line, email));

    event.remove_multi_property(ATTENDEE);
    for attendee in attendees {
        let line = match attendee {
            AttendeeEdit::Kept { email } => stored_line(email).cloned().unwrap_or_else(|| new_attendee(email)),
            AttendeeEdit::Written { email, display_name, role } => {
                written_attendee(stored_line(email), email, display_name, role.as_deref())
            }
        };
        event.append_multi_property(line);
    }
}

pub(crate) fn apply_organizer_change(event: &mut icalendar::Event, change: &OrganizerChange) {
    match change {
        OrganizerChange::Unchanged => {}
        OrganizerChange::Cleared => {
            event.remove_property(ORGANIZER);
        }
        OrganizerChange::Set { email, display_name } => {
            let line = event
                .properties()
                .get(ORGANIZER)
                .filter(|line| has_email(line, email))
                .cloned()
                .unwrap_or_else(|| Property::new(ORGANIZER, mailto(email)));
            event.append_property(with_param(&line, CN_PARAM, display_name.as_deref()));
        }
    }
}

/// The line of an [`AttendeeEdit::Written`]: the stored one, or a new one, with the parameters it changes.
fn written_attendee(
    stored: Option<&Property>,
    email: &str,
    display_name: &NameChange,
    role: Option<&str>,
) -> Property {
    let line = stored.cloned().unwrap_or_else(|| new_attendee(email));
    let line = match display_name {
        NameChange::Unchanged => line,
        NameChange::Set { name } => with_param(&line, CN_PARAM, Some(name)),
        NameChange::Cleared => with_param(&line, CN_PARAM, None),
    };
    match role {
        Some(role) => with_param(&line, ROLE_PARAM, Some(role)),
        None => line,
    }
}

fn new_attendee(email: &str) -> Property {
    Property::new(ATTENDEE, mailto(email))
        .add_parameter("PARTSTAT", "NEEDS-ACTION")
        .add_parameter("RSVP", "TRUE")
        .done()
}

/// `line` with its `key` parameter set to `value`, or removed when `None`.
fn with_param(line: &Property, key: &str, value: Option<&str>) -> Property {
    let mut rebuilt = Property::new(line.key(), line.value());
    for (_, param) in line.params().iter().filter(|(name, _)| !name.eq_ignore_ascii_case(key)) {
        rebuilt.append_parameter(param.clone());
    }
    if let Some(value) = value {
        rebuilt.add_parameter(key, value);
    }
    rebuilt
}

fn has_email(line: &Property, email: &str) -> bool {
    strip_mailto(line.value()).eq_ignore_ascii_case(email)
}

fn mailto(email: &str) -> String {
    format!("mailto:{email}")
}
