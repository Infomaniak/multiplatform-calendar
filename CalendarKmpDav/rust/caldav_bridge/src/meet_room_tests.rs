use crate::events::{build_event_ics, patch_event_ics, upsert_override_vevent};
use crate::models::{
    AlarmsChange, AttendeesChange, ColorChange, DateListChange, EventEdit, OrganizerChange, OverrideRemoval,
    RecurrenceChange, RecurrenceIdSpec,
};

const URL: &str = "https://kmeet.infomaniak.com/abc";
const OTHER_URL: &str = "https://kmeet.infomaniak.com/xyz";

fn event_ics(meet_room: Option<&str>) -> String {
    let meet_room = meet_room.map(|url| format!("X-INFOMANIAK-MEET-ROOM-URL:{url}\r\n")).unwrap_or_default();
    format!(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:event\r\nDTSTART:20260615T100000Z\r\n\
         DTEND:20260615T110000Z\r\nRRULE:FREQ=DAILY;COUNT=5\r\nSUMMARY:Event\r\n{meet_room}END:VEVENT\r\nEND:VCALENDAR\r\n"
    )
}

fn edit(meet_room_url: Option<&str>) -> EventEdit {
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
        meet_room_url: meet_room_url.map(Into::into),
        timezones: vec![],
        color_change: ColorChange::Unchanged,
        recurrence_change: RecurrenceChange::Unchanged,
        ex_date_change: DateListChange::Unchanged,
        r_date_change: DateListChange::Unchanged,
        override_removal: OverrideRemoval::Unchanged,
        alarms_change: AlarmsChange::Unchanged,
        attendees_change: AttendeesChange::Unchanged,
        organizer_change: OrganizerChange::Unchanged,
        stamp: "20260601T000000Z".into(),
    }
}

#[test]
fn patch_adds_the_meet_room_url() {
    let patched = patch_event_ics(&event_ics(None), edit(Some(URL))).unwrap();

    assert_eq!(Some(URL.to_string()), patched.content.meet_room_url);
}

#[test]
fn patch_replaces_the_meet_room_url() {
    let patched = patch_event_ics(&event_ics(Some(URL)), edit(Some(OTHER_URL))).unwrap();

    assert_eq!(Some(OTHER_URL.to_string()), patched.content.meet_room_url);
    assert_eq!(1, patched.ics_data.matches("X-INFOMANIAK-MEET-ROOM-URL").count());
}

#[test]
fn patch_clears_the_meet_room_url() {
    let patched = patch_event_ics(&event_ics(Some(URL)), edit(None)).unwrap();

    assert_eq!(None, patched.content.meet_room_url);
    assert!(!patched.ics_data.contains("X-INFOMANIAK-MEET-ROOM-URL"));
}

#[test]
fn build_writes_the_meet_room_url() {
    let built = build_event_ics(edit(Some(URL)), None).unwrap();

    assert_eq!(Some(URL.to_string()), built.content.meet_room_url);
}

#[test]
fn override_clears_the_meet_room_url_it_inherits() {
    let recurrence_id = RecurrenceIdSpec { tzid: None, is_date_only: false, value: "20260616T100000Z".into() };

    let patched = upsert_override_vevent(&event_ics(Some(URL)), recurrence_id, edit(None), None).unwrap();

    assert_eq!(Some(URL.to_string()), patched.content.meet_room_url);
    assert_eq!(None, patched.overrides[0].content.meet_room_url);
}
