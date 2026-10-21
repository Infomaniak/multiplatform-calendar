use crate::models::{AttendeeEntry, EventContentEntry, EventEntry, OccurrenceSlot, RecurrenceIdSpec, ReplyScope};
use crate::participation::set_participation_status;

const ORGANIZER: &str = "ORGANIZER:mailto:boss@x.com\r\n";
const ME: &str = "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:Me@x.com\r\n";
const BOB: &str = "ATTENDEE;PARTSTAT=DECLINED:mailto:bob@x.com\r\n";
const STAMP: &str = "20260601T000000Z";

fn master(participants: &str) -> String {
    format!(
        "BEGIN:VEVENT\r\nUID:event\r\nDTSTAMP:20260101T000000Z\r\nSEQUENCE:3\r\nDTSTART:20260615T100000Z\r\n\
         DTEND:20260615T110000Z\r\nRRULE:FREQ=DAILY;COUNT=5\r\nSUMMARY:Event\r\n{participants}END:VEVENT\r\n"
    )
}

fn override_on_17th(participants: &str) -> String {
    format!(
        "BEGIN:VEVENT\r\nUID:event\r\nRECURRENCE-ID:20260617T100000Z\r\nDTSTAMP:20260101T000000Z\r\nSEQUENCE:3\r\nDTSTART:20260617T140000Z\r\n\
         DTEND:20260617T150000Z\r\nSUMMARY:Moved\r\n{participants}END:VEVENT\r\n"
    )
}

fn calendar(vevents: &[String]) -> String {
    format!("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n{}END:VCALENDAR\r\n", vevents.concat())
}

fn reply(ics: &str, scope: ReplyScope) -> Result<EventEntry, crate::error::CaldavError> {
    set_participation_status(ics, scope, vec!["me@x.com".into()], "ACCEPTED".into(), STAMP.into())
}

fn on_18th() -> ReplyScope {
    ReplyScope::Occurrence {
        recurrence_id: RecurrenceIdSpec { tzid: None, is_date_only: false, value: "20260618T100000Z".into() },
        slot: OccurrenceSlot {
            dtstart: "20260618T100000Z".into(),
            dtstart_tzid: None,
            dtend: Some("20260618T110000Z".into()),
            dtend_tzid: None,
            all_day: false,
        },
    }
}

fn on_17th() -> ReplyScope {
    ReplyScope::Occurrence {
        recurrence_id: RecurrenceIdSpec { tzid: None, is_date_only: false, value: "20260617T100000Z".into() },
        slot: OccurrenceSlot {
            dtstart: "20260617T100000Z".into(),
            dtstart_tzid: None,
            dtend: Some("20260617T110000Z".into()),
            dtend_tzid: None,
            all_day: false,
        },
    }
}

fn attendee<'a>(content: &'a EventContentEntry, email: &str) -> &'a AttendeeEntry {
    content.attendees.iter().find(|attendee| attendee.email.eq_ignore_ascii_case(email)).unwrap()
}

fn status_of(content: &EventContentEntry, email: &str) -> Option<String> {
    attendee(content, email).status.clone()
}

#[test]
fn series_reply_sets_only_the_user_partstat() {
    let ics = calendar(&[master(&format!("{ORGANIZER}{ME}{BOB}"))]);

    let replied = reply(&ics, ReplyScope::Series).unwrap();

    assert_eq!(Some("ACCEPTED".into()), status_of(&replied.content, "me@x.com"));
    assert!(attendee(&replied.content, "me@x.com").response_needed);
    assert_eq!(Some("DECLINED".into()), status_of(&replied.content, "bob@x.com"));
    assert_eq!(Some("3".into()), replied.content.sequence);
    assert_eq!(Some(STAMP.into()), replied.content.dtstamp);
    assert_eq!(Some(STAMP.into()), replied.content.last_modified);
    assert_eq!(Some("FREQ=DAILY;COUNT=5".into()), replied.rrule);
}

#[test]
fn series_reply_reaches_every_override_listing_the_user() {
    let ics = calendar(&[master(&format!("{ORGANIZER}{ME}")), override_on_17th(&format!("{ORGANIZER}{ME}"))]);

    let replied = reply(&ics, ReplyScope::Series).unwrap();

    assert_eq!(Some("ACCEPTED".into()), status_of(&replied.content, "me@x.com"));
    assert_eq!(Some("ACCEPTED".into()), status_of(&replied.overrides[0].content, "me@x.com"));
}

#[test]
fn series_reply_skips_an_override_not_listing_the_user() {
    let ics = calendar(&[master(&format!("{ORGANIZER}{ME}")), override_on_17th(&format!("{ORGANIZER}{BOB}"))]);

    let replied = reply(&ics, ReplyScope::Series).unwrap();

    assert_eq!(Some("ACCEPTED".into()), status_of(&replied.content, "me@x.com"));
    assert_eq!(Some("20260101T000000Z".into()), replied.overrides[0].content.dtstamp);
}

#[test]
fn reply_fails_when_the_user_is_not_an_attendee() {
    let ics = calendar(&[master(&format!("{ORGANIZER}{BOB}"))]);

    assert!(reply(&ics, ReplyScope::Series).is_err());
    assert!(reply(&ics, on_18th()).is_err());
}

#[test]
fn occurrence_reply_patches_its_existing_override_only() {
    let ics = calendar(&[master(&format!("{ORGANIZER}{ME}")), override_on_17th(&format!("{ORGANIZER}{ME}"))]);

    let replied = reply(&ics, on_17th()).unwrap();

    assert_eq!(Some("NEEDS-ACTION".into()), status_of(&replied.content, "me@x.com"));
    assert_eq!(1, replied.overrides.len());
    let detached = &replied.overrides[0].content;
    assert_eq!(Some("ACCEPTED".into()), status_of(detached, "me@x.com"));
    assert_eq!(Some("20260617T140000Z".into()), detached.dtstart);
    assert_eq!(Some("Moved".into()), detached.summary);
}

#[test]
fn occurrence_reply_detaches_the_instance_with_the_master_sequence() {
    let ics = calendar(&[master(&format!("{ORGANIZER}{ME}{BOB}"))]);

    let replied = reply(&ics, on_18th()).unwrap();

    assert_eq!(Some("NEEDS-ACTION".into()), status_of(&replied.content, "me@x.com"));
    assert_eq!(1, replied.overrides.len());
    let detached = &replied.overrides[0];
    assert_eq!("20260618T100000Z", detached.recurrence_id);
    assert_eq!(Some("20260618T100000Z".into()), detached.content.dtstart);
    assert_eq!(Some("20260618T110000Z".into()), detached.content.dtend);
    assert_eq!(Some("3".into()), detached.content.sequence);
    assert_eq!(Some("Event".into()), detached.content.summary);
    assert_eq!(Some("ACCEPTED".into()), status_of(&detached.content, "me@x.com"));
    assert_eq!(Some("DECLINED".into()), status_of(&detached.content, "bob@x.com"));
    assert_eq!(Some("boss@x.com".into()), detached.content.organizer.as_ref().map(|organizer| organizer.email.clone()));
}
