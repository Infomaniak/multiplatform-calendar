use crate::events::{build_event_ics, patch_event_ics, upsert_override_vevent};
use crate::models::{
    AlarmsChange, AttendeeEdit, AttendeesChange, ColorChange, DateListChange, EventEdit, EventEntry, OrganizerChange,
    OverrideRemoval, RecurrenceChange, RecurrenceIdSpec,
};

const ROOM: &str = "ATTENDEE;CUTYPE=ROOM;PARTSTAT=ACCEPTED;X-VENDOR=v:mailto:room@x.com";
const BOB: &str = "ATTENDEE;CN=Bob;PARTSTAT=DECLINED;ROLE=OPT-PARTICIPANT;SCHEDULE-STATUS=2.0:mailto:Bob@x.com";
const CAROL: &str = "ATTENDEE;CN=Carol;PARTSTAT=ACCEPTED:mailto:carol@x.com";
const ORGANIZER: &str = "ORGANIZER;CN=Boss;SENT-BY=\"mailto:assistant@x.com\":mailto:boss@x.com";

fn event_ics(participants: &str) -> String {
    format!(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:event\r\nDTSTART:20260615T100000Z\r\n\
         DTEND:20260615T110000Z\r\nRRULE:FREQ=DAILY;COUNT=5\r\nSUMMARY:Event\r\n{participants}END:VEVENT\r\nEND:VCALENDAR\r\n"
    )
}

fn stored_participants() -> String {
    [ORGANIZER, ROOM, BOB, CAROL].map(|line| format!("{line}\r\n")).concat()
}

fn edit(attendees_change: AttendeesChange, organizer_change: OrganizerChange) -> EventEdit {
    EventEdit {
        summary: Some("Event".into()),
        dtstart: "20260615T100000Z".into(),
        dtstart_tzid: None,
        dtend: Some("20260615T110000Z".into()),
        dtend_tzid: None,
        all_day: false,
        location: None,
        description: None,
        transp: None,
        timezones: vec![],
        color_change: ColorChange::Unchanged,
        recurrence_change: RecurrenceChange::Unchanged,
        ex_date_change: DateListChange::Unchanged,
        r_date_change: DateListChange::Unchanged,
        override_removal: OverrideRemoval::Unchanged,
        alarms_change: AlarmsChange::Unchanged,
        attendees_change,
        organizer_change,
        stamp: "20260601T000000Z".into(),
    }
}

fn kept(email: &str) -> AttendeeEdit {
    AttendeeEdit::Kept { email: email.into() }
}

fn written(email: &str, display_name: Option<&str>, role: &str) -> AttendeeEdit {
    AttendeeEdit::Written { email: email.into(), display_name: display_name.map(Into::into), role: role.into() }
}

fn attendees(change: Vec<AttendeeEdit>) -> AttendeesChange {
    AttendeesChange::Set { attendees: change }
}

fn unfolded(entry: &EventEntry) -> String {
    entry.ics_data.replace("\r\n ", "")
}

fn emails(entry: &EventEntry) -> Vec<String> {
    entry.content.attendees.iter().map(|attendee| attendee.email.clone()).collect()
}

#[test]
fn unchanged_attendees_and_organizer_are_left_verbatim() {
    let patched = patch_event_ics(
        &event_ics(&stored_participants()),
        edit(AttendeesChange::Unchanged, OrganizerChange::Unchanged),
    )
    .unwrap();

    let ics = unfolded(&patched);
    for line in [ORGANIZER, ROOM, BOB, CAROL] {
        assert!(ics.contains(line), "{line} lost in {ics}");
    }
}

#[test]
fn set_keeps_written_adds_and_drops_attendees_in_the_stated_order() {
    let change = attendees(vec![
        written("dave@x.com", Some("Dave"), "REQ-PARTICIPANT"),
        kept("room@x.com"),
        written("bob@x.com", None, "REQ-PARTICIPANT"),
    ]);

    let patched = patch_event_ics(&event_ics(&stored_participants()), edit(change, OrganizerChange::Unchanged)).unwrap();

    let ics = unfolded(&patched);
    assert_eq!(emails(&patched), ["dave@x.com", "room@x.com", "Bob@x.com"]);
    assert!(ics.contains(ROOM), "a kept line stays verbatim: {ics}");
    assert!(
        ics.contains("ATTENDEE;PARTSTAT=DECLINED;ROLE=REQ-PARTICIPANT;SCHEDULE-STATUS=2.0:mailto:Bob@x.com"),
        "a written line keeps its other parameters, CN dropped and ROLE rewritten: {ics}",
    );
    assert!(
        ics.contains("ATTENDEE;CN=Dave;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:dave@x.com"),
        "a new attendee awaits a response: {ics}",
    );
    assert!(!ics.contains("carol@x.com"), "an attendee left out is removed: {ics}");
}

#[test]
fn set_with_no_attendee_clears_the_list() {
    let patched =
        patch_event_ics(&event_ics(&stored_participants()), edit(attendees(vec![]), OrganizerChange::Unchanged))
            .unwrap();

    assert!(patched.content.attendees.is_empty());
    assert!(!patched.ics_data.contains("ATTENDEE"));
}

#[test]
fn organizer_set_on_the_same_email_only_rewrites_its_name() {
    let change = OrganizerChange::Set { email: "BOSS@x.com".into(), display_name: Some("Big boss".into()) };

    let patched = patch_event_ics(&event_ics(&stored_participants()), edit(AttendeesChange::Unchanged, change)).unwrap();

    assert!(
        unfolded(&patched).contains("ORGANIZER;CN=Big boss;SENT-BY=\"mailto:assistant@x.com\":mailto:boss@x.com"),
        "{}",
        patched.ics_data,
    );
}

#[test]
fn organizer_set_on_another_email_replaces_it() {
    let change = OrganizerChange::Set { email: "new@x.com".into(), display_name: None };

    let patched = patch_event_ics(&event_ics(&stored_participants()), edit(AttendeesChange::Unchanged, change)).unwrap();

    let ics = unfolded(&patched);
    assert!(ics.contains("ORGANIZER:mailto:new@x.com"), "{ics}");
    assert!(!ics.contains("SENT-BY"), "{ics}");
}

#[test]
fn organizer_cleared_is_removed() {
    let patched = patch_event_ics(
        &event_ics(&stored_participants()),
        edit(AttendeesChange::Unchanged, OrganizerChange::Cleared),
    )
    .unwrap();

    assert!(patched.content.organizer.is_none());
}

#[test]
fn build_writes_the_attendees_and_organizer() {
    let built = build_event_ics(
        edit(
            attendees(vec![written("bob@x.com", Some("Bob"), "OPT-PARTICIPANT")]),
            OrganizerChange::Set { email: "me@x.com".into(), display_name: Some("Me".into()) },
        ),
        None,
    )
    .unwrap();

    let ics = unfolded(&built);
    assert!(ics.contains("ORGANIZER;CN=Me:mailto:me@x.com"), "{ics}");
    assert!(ics.contains("ATTENDEE;CN=Bob;PARTSTAT=NEEDS-ACTION;ROLE=OPT-PARTICIPANT;RSVP=TRUE:mailto:bob@x.com"), "{ics}");
}

#[test]
fn detaching_an_occurrence_edits_the_attendees_it_takes_from_the_master() {
    let recurrence_id = RecurrenceIdSpec { tzid: None, is_date_only: false, value: "20260617T100000Z".into() };
    let change = attendees(vec![kept("room@x.com"), written("dave@x.com", None, "REQ-PARTICIPANT")]);

    let patched = upsert_override_vevent(
        &event_ics(&stored_participants()),
        recurrence_id,
        edit(change, OrganizerChange::Unchanged),
        None,
    )
    .unwrap();

    let ics = unfolded(&patched);
    let (master, detached) = ics.split_at(ics.rfind("BEGIN:VEVENT").unwrap());
    assert!(master.contains(BOB) && master.contains(CAROL), "the master keeps its own list: {master}");
    assert!(detached.contains(ROOM) && detached.contains(ORGANIZER), "{detached}");
    assert!(detached.contains("mailto:dave@x.com") && !detached.contains("carol@x.com"), "{detached}");
}

#[test]
fn parsing_reads_the_user_type() {
    let patched = patch_event_ics(
        &event_ics(&stored_participants()),
        edit(AttendeesChange::Unchanged, OrganizerChange::Unchanged),
    )
    .unwrap();

    let user_types: Vec<_> = patched.content.attendees.iter().map(|attendee| attendee.user_type.as_deref()).collect();
    assert_eq!(user_types, [Some("ROOM"), None, None]);
}
