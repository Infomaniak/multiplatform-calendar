use crate::events::{build_event_ics, patch_event_ics, upsert_override_vevent};
use crate::models::{
    AlarmsChange, AttendeesChange, ClassificationChange, ColorChange, DateListChange, EventEdit, OrganizerChange,
    OverrideRemoval, RecurrenceChange, RecurrenceIdSpec,
};

fn event_ics(class: Option<&str>) -> String {
    let class = class.map(|value| format!("CLASS:{value}\r\n")).unwrap_or_default();
    format!(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:event\r\nDTSTART:20260615T100000Z\r\n\
         DTEND:20260615T110000Z\r\nRRULE:FREQ=DAILY;COUNT=5\r\nSUMMARY:Event\r\n{class}END:VEVENT\r\nEND:VCALENDAR\r\n"
    )
}

fn edit(classification_change: ClassificationChange) -> EventEdit {
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
        bookable_uuid: None,
        timezones: vec![],
        color_change: ColorChange::Unchanged,
        classification_change,
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

fn private() -> ClassificationChange {
    ClassificationChange::Set { value: "PRIVATE".into() }
}

#[test]
fn patch_sets_the_class() {
    let patched = patch_event_ics(&event_ics(None), edit(private())).unwrap();

    assert_eq!(Some("PRIVATE".to_string()), patched.content.classification);
}

#[test]
fn patch_replaces_the_class() {
    let patched = patch_event_ics(&event_ics(Some("CONFIDENTIAL")), edit(private())).unwrap();

    assert_eq!(Some("PRIVATE".to_string()), patched.content.classification);
    assert_eq!(1, patched.ics_data.matches("CLASS:").count());
}

#[test]
fn patch_clears_the_class() {
    let patched = patch_event_ics(&event_ics(Some("PRIVATE")), edit(ClassificationChange::Cleared)).unwrap();

    assert_eq!(None, patched.content.classification);
    assert!(!patched.ics_data.contains("CLASS:"));
}

#[test]
fn unchanged_patch_keeps_an_unmodelled_class() {
    let patched = patch_event_ics(&event_ics(Some("CONFIDENTIAL")), edit(ClassificationChange::Unchanged)).unwrap();

    assert_eq!(Some("CONFIDENTIAL".to_string()), patched.content.classification);
}

#[test]
fn build_writes_the_class() {
    let built = build_event_ics(edit(private()), None).unwrap();

    assert_eq!(Some("PRIVATE".to_string()), built.content.classification);
}

#[test]
fn override_clears_the_class_it_inherits() {
    let recurrence_id = RecurrenceIdSpec { tzid: None, is_date_only: false, value: "20260616T100000Z".into() };

    let patched =
        upsert_override_vevent(&event_ics(Some("PRIVATE")), recurrence_id, edit(ClassificationChange::Cleared), None)
            .unwrap();

    assert_eq!(Some("PRIVATE".to_string()), patched.content.classification);
    assert_eq!(None, patched.overrides[0].content.classification);
}
