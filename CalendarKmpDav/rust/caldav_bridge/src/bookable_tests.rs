use crate::events::{build_event_ics, patch_event_ics, upsert_override_vevent};
use crate::models::{
    AlarmsChange, AttendeesChange, ColorChange, DateListChange, EventEdit, OrganizerChange, OverrideRemoval,
    RecurrenceChange, RecurrenceIdSpec,
};

const UUID: &str = "9d3e6081-0000-0000-0000-000000000001";
const OTHER_UUID: &str = "9d3e6123-0000-0000-0000-000000000002";

fn event_ics(bookable: Option<&str>) -> String {
    let bookable = bookable.map(|uuid| format!("X-INFOMANIAK-BOOKABLE:{uuid}\r\n")).unwrap_or_default();
    format!(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:event\r\nDTSTART:20260615T100000Z\r\n\
         DTEND:20260615T110000Z\r\nRRULE:FREQ=DAILY;COUNT=5\r\nSUMMARY:Event\r\n{bookable}END:VEVENT\r\nEND:VCALENDAR\r\n"
    )
}

fn edit(bookable_uuid: Option<&str>) -> EventEdit {
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
        meet_room_url: None,
        bookable_uuid: bookable_uuid.map(Into::into),
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
fn patch_adds_the_bookable() {
    let patched = patch_event_ics(&event_ics(None), edit(Some(UUID))).unwrap();

    assert_eq!(Some(UUID.to_string()), patched.content.bookable_uuid);
}

#[test]
fn patch_replaces_the_bookable() {
    let patched = patch_event_ics(&event_ics(Some(UUID)), edit(Some(OTHER_UUID))).unwrap();

    assert_eq!(Some(OTHER_UUID.to_string()), patched.content.bookable_uuid);
    assert_eq!(1, patched.ics_data.matches("X-INFOMANIAK-BOOKABLE").count());
}

#[test]
fn patch_clears_the_bookable() {
    let patched = patch_event_ics(&event_ics(Some(UUID)), edit(None)).unwrap();

    assert_eq!(None, patched.content.bookable_uuid);
    assert!(!patched.ics_data.contains("X-INFOMANIAK-BOOKABLE"));
}

#[test]
fn build_writes_the_bookable() {
    let built = build_event_ics(edit(Some(UUID)), None).unwrap();

    assert_eq!(Some(UUID.to_string()), built.content.bookable_uuid);
}

#[test]
fn override_clears_the_bookable_it_inherits() {
    let recurrence_id = RecurrenceIdSpec { tzid: None, is_date_only: false, value: "20260616T100000Z".into() };

    let patched = upsert_override_vevent(&event_ics(Some(UUID)), recurrence_id, edit(None), None).unwrap();

    assert_eq!(Some(UUID.to_string()), patched.content.bookable_uuid);
    assert_eq!(None, patched.overrides[0].content.bookable_uuid);
}
